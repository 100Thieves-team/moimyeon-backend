package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomApplicationManager(
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val participationRepository: ParticipationRepository,
    private val participationValidator: ParticipationValidator,
    private val participationFinder: ParticipationFinder,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    // 수락. 룸 행에 쓰기 잠금을 걸어 동시 수락을 직렬화한 뒤(마지막 자리 1건만 성공, §4.4),
    // 방장 권한·신청 상태·모집 여부·신청자 참여 슬롯·정원을 확인하고 신청자를 참여자로 등록한다.
    //
    // ⚠️ 신청자 회원 행은 잠그지 않는다(MOI-427 D4). 두 룸의 방장이 같은 신청자를 동시에 수락하면
    //    둘 다 슬롯 여유를 보고 통과해 슬롯이 4개가 될 수 있다. 신청 제출 경로는 회원 행 락 안에서
    //    판정하므로 막는 쪽은 정확하고, 새는 곳은 여기 하나다. 락을 넣는다면 반드시 회원 → 룸 순서다
    //    (제출 경로가 그 순서라 반대로 넣으면 데드락이 난다).
    @Transactional
    fun accept(roomId: UUID, applicationId: Long, hostMemberId: UUID): ApplicationDecision {
        val room = loadRoomForUpdateAsHost(roomId, hostMemberId)
        requireBusiness(room.status == RoomStatus.RECRUITING, CoreErrorType.ROOM_NOT_RECRUITING)

        val application = loadPendingApplication(roomId, applicationId)

        val now = LocalDateTime.now(clock)
        val current = participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED).toInt()

        // 정원보다 먼저 본다. 슬롯이 찬 신청은 자리가 나도 살아날 수 없어, 뒤에 두면 "정원 때문에 실패 →
        // 자리 나면 다시 실패"가 반복된다. 여기서 예외를 던지면 아래 종료가 함께 롤백되어 신청이 대기로
        // 남으므로(그 행이 방장 목록을 영영 막는다) 거부를 결정으로 돌려준다(MOI-427 결정 20260813 (b), D2).
        if (!participationFinder.hasAvailableSlot(application.applicantMemberId)) {
            application.closeBySlotExceeded(now)
            return ApplicationDecision(
                applicationId = application.id,
                status = application.status,
                currentParticipants = current,
                maxCapacity = room.maxCapacity.toInt(),
            )
        }

        requireBusiness(current < room.maxCapacity, CoreErrorType.ROOM_CAPACITY_FULL)

        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = application.applicantMemberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
        application.accept(hostMemberId, now)
        applicationEventPublisher.publishEvent(
            RoomApplicationAcceptedEvent(
                applicationId = application.id,
                roomId = roomId,
                applicantMemberId = application.applicantMemberId,
            ),
        )

        return ApplicationDecision(
            applicationId = application.id,
            status = application.status,
            currentParticipants = current + 1,
            maxCapacity = room.maxCapacity.toInt(),
        )
    }

    // 반려. 정원·참여자에 영향이 없어 룸 잠금은 필요 없다. 방장 권한과 대기 상태만 확인한다.
    @Transactional
    fun reject(roomId: UUID, applicationId: Long, hostMemberId: UUID, reason: String?): ApplicationDecision {
        val room = loadActiveRoomAsHost(roomId, hostMemberId)
        val application = loadPendingApplication(roomId, applicationId)

        application.reject(hostMemberId, reason, LocalDateTime.now(clock))

        val current = participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED).toInt()
        return ApplicationDecision(
            applicationId = application.id,
            status = application.status,
            currentParticipants = current,
            maxCapacity = room.maxCapacity.toInt(),
        )
    }

    private fun loadPendingApplication(roomId: UUID, applicationId: Long): RoomApplicationEntity {
        val application = requireFound(
            roomApplicationRepository.findByIdAndRoomIdAndDeletedAtIsNull(applicationId, roomId),
            CoreErrorType.APPLICATION_NOT_FOUND,
        )
        requireBusiness(application.isPending(), CoreErrorType.APPLICATION_ALREADY_HANDLED)
        return application
    }

    private fun loadRoomForUpdateAsHost(roomId: UUID, memberId: UUID): RoomEntity {
        val room = requireFound(
            roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireHost(roomId, memberId)
        return room
    }

    private fun loadActiveRoomAsHost(roomId: UUID, memberId: UUID): RoomEntity {
        val room = requireFound(
            roomRepository.findById(roomId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireHost(roomId, memberId)
        return room
    }

    // 방장 판정은 ParticipationValidator 한 곳이 소유한다 — 상태를 함께 봐야 하는데
    // 네 곳에 흩어져 있던 것이 셋만 고쳐지고 하나가 남는 사고를 막는다(MOI-397).
    private fun requireHost(roomId: UUID, memberId: UUID) {
        participationValidator.validateHost(roomId, memberId)
    }
}
