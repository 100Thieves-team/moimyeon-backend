package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.member.MemberValidator
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
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

@Component
class RoomManager(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val participationValidator: ParticipationValidator,
    private val memberValidator: MemberValidator,
    private val clock: Clock,
) {
    // 쓰기 넷이 한 커밋이다. 방장의 이력서는 신청 행을 거쳐 제출로 보존되므로(MOI-333)
    // 신청 행이 없으면 제출도 없다 — 넷 중 하나라도 실패하면 방장 없는 룸이 남지 않아야 한다.
    // 네 시각은 "같은 순간"이 곧 명세라 한 번만 찍어 나눠 쓴다.
    //
    // 중복 생성 제한(MOI-330)은 이 경계 안에서 본다. 밖에서 미리 세면 커밋 밖의 확인이라 확정이 아니고,
    // 거부됐을 때 앞선 쓰기가 남을 자리가 생긴다.
    //
    // 판정 순서가 곧 사용자가 받는 결과다(MOI-331 D4, MOI-447 D1-1): 멱등 → 참여 슬롯 → 활성 3개.
    // 중복 확인이 두 한도보다 **앞**이다 — 이미 있는 룸을 돌려주는 것은 새 자원을 만들지 않으므로
    // 한도와 무관하다. 슬롯이 활성 3개보다 앞인 것은 신청 경로(MOI-427 D8)와 같은 논리다 —
    // 둘 다 초과면 "참여 중인 룸을 정리하라"가 정확한 안내다.
    //
    // ⚠️ validateActive 가 잡는 방장 회원 행 잠금이 이 방장의 생성을 직렬화한다. 이것이 멱등(F1·F2)과
    //    3개 제한(F3) 둘 다의 동시성 방어선이다. 자연키에는 DB 유니크가 없다(계획서 D1).
    //    **이 한 줄이 빠져도 어떤 테스트도 빨간불이 되지 않는다** — 순차 재호출은 아래 중복 확인만으로
    //    통과하기 때문이다(testing.md: 레이스는 재현하지 않는다). 지우지 않는다.
    //    새 생성 경로(일괄 생성 등)를 만들면 반드시 이 잠금을 함께 가져간다.
    @Transactional
    fun create(room: Room, hostMemberId: UUID, resumeId: UUID, resumeFile: ResumeFile): RoomCreationResult {
        memberValidator.validateActive(hostMemberId)

        findDuplicate(hostMemberId, room)?.let { return RoomCreationResult(it.id, it.status) }

        // 방장 참여도 슬롯을 문다(「룸 참여」 §4.1 "방장 포함", MOI-446). 이게 빠지면
        // 참여자로 3개 + 방장으로 3개 = 6개가 된다 — 신청·수락 경로(MOI-427)만으로는 못 막는다.
        participationValidator.validateSlotAvailable(hostMemberId)

        // ⚠️ 같은 공고 활성 룸은 전부 내 점유 슬롯이기도 해서(부분집합) 두 상한이 같은 3 인 지금은
        //    이 게이트에 걸리기 전에 슬롯 게이트가 항상 먼저 걸린다 — 어떤 테스트도 이 줄을 빨간불로
        //    만들지 못한다. 그래도 지우지 않는다: 한도가 회원별로 개인화되어 두 상한이 갈라지는 날
        //    (PRD 예고) 다시 실효하는 정책 축이고, 사전 조회(getCreationLimit)가 이 규칙으로 경고한다.
        requireBusiness(
            !ActiveRoomLimit.isExceeded(countActiveHostedRooms(hostMemberId, room.jobPostingId, room.jobRoleId)),
            CoreErrorType.ACTIVE_ROOM_LIMIT_EXCEEDED,
        )

        val now = LocalDateTime.now(clock)

        // Room.create 가 트랜잭션 밖에서 이미 본 규칙을 여기서 다시 본다. 요청을 받고 커밋하기까지
        // 사이에 일정이 과거가 되는 것이 완료 조건이라, 판정이 커밋 경계 안에 있어야 확정이다.
        requireBusiness(room.schedule.startAt.isAfter(now), CoreErrorType.ROOM_START_AT_NOT_FUTURE)

        roomRepository.save(RoomMapper.toEntity(room))
        participationRepository.save(
            ParticipationEntity(
                roomId = room.id,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
        // 제출 행이 참조할 신청 id 를 먼저 확정한다(room_application_id 는 NOT NULL).
        val application = roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity.forHost(room.id, hostMemberId, now),
        )
        resumeSubmissionRepository.save(
            ResumeSubmissionEntity(
                roomApplicationId = application.id,
                roomId = room.id,
                memberId = hostMemberId,
                sourceResumeId = resumeId,
                fileKey = resumeFile.key,
                originalName = resumeFile.originalName,
                sizeBytes = resumeFile.sizeBytes,
                contentType = resumeFile.contentType,
                submittedAt = now,
            ),
        )
        // TODO(BE-05 잔여): chat_room — 엔티티 생성 필요.
        // room_status_log 의 생성 전이는 만들지 않는다(MOI-397). 최초 방장은 participation.role=HOST 가
        // 보존하고, 멱등용으로 되살려도 room_id 를 매번 새로 뽑는 이상 중복 생성을 막지 못한다(MOI-331).
        return RoomCreationResult(room.id, room.status)
    }

    // 편집 가능한 필드 수정. 방장만 가능. 오프라인 지역 참조 검증은 RoomService 가 트랜잭션 밖에서 한다.
    @Transactional
    fun update(roomId: UUID, hostMemberId: UUID, command: RoomUpdateCommand) {
        val room = loadActiveRoomAsHost(roomId, hostMemberId)

        // 확정 이후 변경은 CS 문의로만 푼다(「진행 확정」§4.3).
        requireBusiness(room.status == RoomStatus.RECRUITING, CoreErrorType.ROOM_NOT_EDITABLE)

        // RoomCapacity 는 DB 를 모르는 값 객체라 현재 인원과의 비교는 여기서 한다.
        // 최소 인원은 막지 않는다 — 확정이 미뤄질 뿐 지금 상태를 깨지 않는다.
        val current = participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED).toInt()
        requireBusiness(command.capacity.max >= current, CoreErrorType.ROOM_CAPACITY_BELOW_PARTICIPANTS)

        val (meetingType, sigunguId) = command.meetingPlace.toEntityValues()
        room.update(
            title = command.title.value,
            description = command.description?.value,
            interviewStage = command.interviewStage,
            interviewType = command.interviewType,
            meetingType = meetingType,
            sigunguId = sigunguId,
            minCapacity = command.capacity.min.toShort(),
            maxCapacity = command.capacity.max.toShort(),
            startAt = command.schedule.startAt,
            durationMinutes = command.schedule.durationMinutes.toShort(),
        )
    }

    // 방장이 모집을 접는다. 참여자가 있으면 접을 수 없고 나가기(MOI-397)로 넘겨야 한다.
    //
    // 룸 행 잠금이 취소를 수락·신청 제출과 직렬화한다(셋 다 findByIdForUpdate 를 쓴다).
    // ⚠️ 이 잠금이 빠져도 예외가 나지 않는다: 취소된 룸에 대기 신청이 조용히 남고 그 신청자는
    //    대기 한도 한 칸을 영원히 물고 있게 된다. 테스트로 드러나지 않으므로 지우지 않는다.
    @Transactional
    fun cancel(roomId: UUID, hostMemberId: UUID) {
        val room = loadRoomForUpdateAsHost(roomId, hostMemberId)
        requireBusiness(room.canCancel(), CoreErrorType.ROOM_NOT_RECRUITING)
        requireBusiness(!hasParticipant(roomId), CoreErrorType.ROOM_HAS_PARTICIPANTS)

        cancelWithoutGuard(room, hostMemberId, LocalDateTime.now(clock))
    }

    // 취소의 부수효과만. 방장 판정·룸 잠금·취소 가능 여부는 호출부가 이미 봤다는 전제다.
    // 나가기(MOI-397)가 위임 대상이 없을 때 이것을 공유한다 — 복제하면 셋 중 하나가 빠진다.
    //
    // 순서를 지킨다. 벌크의 flushAutomatically 가 앞의 두 쓰기를 먼저 내보내고,
    // 호출 트랜잭션이 RoomApplicationEntity 를 로드했다면 벌크 뒤 그 행을 다시 읽으면 안 된다.
    fun cancelWithoutGuard(room: RoomEntity, handlerMemberId: UUID, now: LocalDateTime) {
        room.cancel()
        roomStatusLogRepository.save(
            RoomStatusLogEntity.byMember(
                roomId = room.id,
                transitionType = RoomStatus.CANCELED,
                handlerMemberId = handlerMemberId,
                occurredAt = now,
            ),
        )
        roomApplicationRepository.closeAllPending(room.id, RoomApplicationStatus.ROOM_CANCELED, now)
    }

    // 방장이 진행을 확정한다. 여기서부터 참여자·정보가 고정되고(§4.2) MOI-394 가 깔아 둔
    // 수정·신청·수락 게이트가 발효한다.
    //
    // 조건 판정은 RoomConfirmation 이 소유한다 — 룸 상세 조회(F1 버튼)와 같은 함수를 타야
    // 화면 상태와 서버 결과가 어긋나지 않는다. 그래서 여기서 status 를 다시 비교하지 않는다.
    // 락은 취소와 같은 이유로 잡는다(취소·수락·신청 제출이 모두 같은 룸 행을 잠근다).
    @Transactional
    fun confirm(roomId: UUID, hostMemberId: UUID) {
        val entity = loadRoomForUpdateAsHost(roomId, hostMemberId)
        val currentParticipants =
            participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED).toInt()

        val now = LocalDateTime.now(clock)
        RoomConfirmation.of(
            status = entity.status,
            startAt = entity.startAt,
            minCapacity = entity.minCapacity.toInt(),
            currentParticipants = currentParticipants,
            now = now,
        ).blockReason?.let { throw CoreException(it.toErrorType()) }

        entity.confirm()
        roomStatusLogRepository.save(
            RoomStatusLogEntity.byMember(
                roomId = roomId,
                transitionType = RoomStatus.CONFIRMED,
                handlerMemberId = hostMemberId,
                occurredAt = now,
            ),
        )
        roomApplicationRepository.closeAllPending(roomId, RoomApplicationStatus.ROOM_CONFIRMED, now)
    }

    // 상태 계열 넷은 E1410 으로 뭉친다 — 화면이 새로고침하면 정확한 상태를 다시 받으므로
    // 코드를 넷으로 가를 실익이 없다. 안내가 달라지는 둘만 가른다.
    private fun RoomConfirmationBlockReason.toErrorType(): CoreErrorType = when (this) {
        RoomConfirmationBlockReason.ROOM_CONFIRMED,
        RoomConfirmationBlockReason.ROOM_IN_PROGRESS,
        RoomConfirmationBlockReason.ROOM_COMPLETED,
        RoomConfirmationBlockReason.ROOM_CANCELED,
        -> CoreErrorType.ROOM_NOT_RECRUITING

        RoomConfirmationBlockReason.BELOW_MIN_CAPACITY -> CoreErrorType.ROOM_BELOW_MIN_CAPACITY
        RoomConfirmationBlockReason.SCHEDULE_PASSED -> CoreErrorType.ROOM_SCHEDULE_PASSED_FOR_CONFIRMATION
    }

    // 중복 생성 판정(MOI-331 F1·F2). 자연키는 (방장, 공고, 직무, 시각) 이고 활성 집합은 3개 제한과 같다 —
    // 같아야 한다. 취소한 룸을 같은 조건으로 다시 만드는 것은 허용해야 하므로 CANCELED 는 빠져 있다.
    private fun findDuplicate(hostMemberId: UUID, room: Room): RoomEntity? {
        return roomRepository.findActiveHostedRooms(
            hostMemberId,
            room.jobPostingId,
            room.jobRoleId,
            room.schedule.startAt,
            ActiveRoomLimit.ACTIVE_STATUSES,
        ).firstOrNull()
    }

    // 묻는 쪽(RoomFinder)과 같은 쿼리·같은 술어를 써야 화면의 경고와 생성 결과가 어긋나지 않는다.
    private fun countActiveHostedRooms(hostMemberId: UUID, jobPostingId: Long, jobRoleId: Long): Long {
        return roomRepository.countActiveHostedRooms(hostMemberId, jobPostingId, jobRoleId, ActiveRoomLimit.ACTIVE_STATUSES)
    }

    // 방장 외 참여자가 남아 있는가. 방장도 참여 행을 갖기 때문에 역할로 좁혀야 한다.
    private fun hasParticipant(roomId: UUID): Boolean {
        return participationRepository.existsByRoomIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
            roomId,
            ParticipationRole.PARTICIPANT,
            ParticipationStatus.JOINED,
        )
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

    private fun MeetingPlace.toEntityValues(): Pair<MeetingType, Long?> = when (this) {
        MeetingPlace.Online -> MeetingType.ONLINE to null
        is MeetingPlace.Offline -> MeetingType.OFFLINE to sigunguId
    }
}
