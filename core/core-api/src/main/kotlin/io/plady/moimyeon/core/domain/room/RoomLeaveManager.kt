package io.plady.moimyeon.core.domain.room

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.event.OutboxEventPublisher
import io.plady.moimyeon.core.event.payload.RoomHostDelegatedEventPayload
import io.plady.moimyeon.core.event.payload.RoomRecruitingReopenedEventPayload
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

// 나가기는 방장도 참여자도 하는 행위라 RoomManager(생성·수정·취소·확정)의 일만은 아니다.
// 패키지는 room 에 둔다 — 위임 대상이 없으면 룸을 취소하는 데까지 간다.
@Component
class RoomLeaveManager(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val memberFinder: MemberFinder,
    private val participationFinder: ParticipationFinder,
    private val roomManager: RoomManager,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val clock: Clock,
) {
    // 룸 행 잠금이 나가기끼리를 직렬화한다.
    // ⚠️ 이 잠금이 빠져도 예외가 나지 않는다: 둘이 동시에 같은 인원을 보고 나가면
    //    모집 재개 판정이 둘 다 빗나가 확정된 룸이 최소 밑으로 남는다. 결과 예외가 없어 테스트로도 드러나지 않는다.
    @Transactional
    fun leave(roomId: UUID, memberId: UUID) {
        log.debug { "room-leave.manager.leave roomId=$roomId memberId=$memberId" }
        val room = loadRoomForUpdate(roomId)
        requireBusiness(room.canLeave(), CoreErrorType.ROOM_ALREADY_CLOSED)

        val participation = requireFound(
            participationRepository.findByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            ),
            CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN,
        )
        val headcountBeforeLeave = participationRepository
            .countByRoomIdAndStatusAndDeletedAtIsNull(room.id, ParticipationStatus.JOINED)

        val now = LocalDateTime.now(clock)
        val wasConfirmed = room.status == RoomStatus.CONFIRMED
        participation.leave(now, memberId)
        if (participation.participationRole == ParticipationRole.HOST) {
            delegateOrCancel(room, memberId, now, wasConfirmed)
        } else {
            reopenIfBelowMinCapacity(room, memberId, headcountBeforeLeave - 1, now)
        }
    }

    // 방장이 나가면 룸을 남기는 쪽을 먼저 시도한다(PRD §3) — 참여자 → 대기 신청자 → 취소.
    // 이 메서드가 leave 와 한 트랜잭션이라는 것이 핵심이다: 방장 없는 룸이 한순간도 존재하지 않는다.
    private fun delegateOrCancel(
        room: RoomEntity,
        leavingHostId: UUID,
        now: LocalDateTime,
        wasConfirmed: Boolean,
    ) {
        val newHostId = promoteEarliestParticipant(room.id)
            ?: promoteEarliestEligibleApplicant(room.id, leavingHostId, now)
        if (newHostId == null) {
            roomManager.cancelWithoutGuard(room, leavingHostId, now)
            return
        }
        if (wasConfirmed) {
            reopenRecruiting(room, leavingHostId, now)
        }
        outboxEventPublisher.publish(
            EventType.ROOM_HOST_DELEGATED,
            RoomHostDelegatedEventPayload(
                roomId = room.id,
                roomTitle = room.title,
                previousHostMemberId = leavingHostId,
                newHostMemberId = newHostId,
                // 나간 방장은 이미 LEFT 라 여기서 빠진다.
                participantMemberIds = participationFinder.getJoinedParticipants(room.id).map { it.memberId },
            ),
        )
    }

    private fun promoteEarliestParticipant(roomId: UUID): UUID? {
        val next = participationRepository
            .findFirstByRoomIdAndParticipationRoleAndStatusAndDeletedAtIsNullOrderByJoinedAtAscIdAsc(
                roomId,
                ParticipationRole.PARTICIPANT,
                ParticipationStatus.JOINED,
            ) ?: return null
        next.promoteToHost()
        return next.memberId
    }

    // 자격은 제재와 참여 슬롯을 본다(PRD 「룸 참여」 §3). 둘 다 "막지 않고 묻는" 판정이라 예외 없이 건너뛴다.
    // 슬롯이 찬 사람을 승격시키면 그 사람의 참여 중인 룸이 넷이 된다(MOI-427).
    // 건너뛴 신청은 대기로 남긴다 — 방장의 판단이 아니고, 위임에 실패해도 그 룸은 계속 살아 있다.
    private fun promoteEarliestEligibleApplicant(roomId: UUID, leavingHostId: UUID, now: LocalDateTime): UUID? {
        val application = roomApplicationRepository
            .findByRoomIdAndStatusAndDeletedAtIsNullOrderByAppliedAtAscIdAsc(roomId, RoomApplicationStatus.PENDING)
            .firstOrNull {
                memberFinder.isActive(it.applicantMemberId) &&
                    participationFinder.hasAvailableSlot(it.applicantMemberId)
            }
            ?: return null

        // 수락 처리자는 나가는 방장이다 — 실제로 그가 넘긴 자리다.
        application.accept(leavingHostId, now)
        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = application.applicantMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
        return application.applicantMemberId
    }

    // 확정은 "이 인원으로 진행한다"는 약속이다. 참여자가 빠져 최소 밑이 되면 약속을 풀고 다시 모은다(「룸 참여」 R120).
    // 예정 시각이 지난 뒤에는 되돌리지 않는다 — 신청도 재확정도 못 해 룸이 모집 중에 멈춘다(R183).
    private fun reopenIfBelowMinCapacity(
        room: RoomEntity,
        leavingMemberId: UUID,
        remainingHeadcount: Long,
        now: LocalDateTime,
    ) {
        if (room.status != RoomStatus.CONFIRMED) return
        if (RoomSchedule.isPassed(room.startAt, now)) return
        if (remainingHeadcount >= room.minCapacity) return

        reopenRecruiting(room, leavingMemberId, now)
        val remaining = participationFinder.getJoinedParticipants(room.id)
        outboxEventPublisher.publish(
            EventType.ROOM_RECRUITING_REOPENED,
            RoomRecruitingReopenedEventPayload(
                roomId = room.id,
                roomTitle = room.title,
                hostMemberId = remaining.single { it.isHost }.memberId,
                participantMemberIds = remaining.map { it.memberId },
            ),
        )
    }

    private fun reopenRecruiting(room: RoomEntity, handlerMemberId: UUID, now: LocalDateTime) {
        room.reopenRecruiting()
        roomStatusLogRepository.save(
            RoomStatusLogEntity.byMember(
                roomId = room.id,
                transitionType = RoomStatus.RECRUITING,
                handlerMemberId = handlerMemberId,
                occurredAt = now,
            ),
        )
    }

    private fun loadRoomForUpdate(roomId: UUID): RoomEntity = requireFound(
        roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() },
        CoreErrorType.ROOM_NOT_FOUND,
    )
}
