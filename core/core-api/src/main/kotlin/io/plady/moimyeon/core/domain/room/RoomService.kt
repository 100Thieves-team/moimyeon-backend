package io.plady.moimyeon.core.domain.room

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.domain.jobposting.JobPostingFinder
import io.plady.moimyeon.core.domain.resume.ResumeValidator
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class RoomService(
    private val catalogRefValidator: CatalogRefValidator,
    private val resumeValidator: ResumeValidator,
    private val roomManager: RoomManager,
    private val roomFinder: RoomFinder,
    private val roomSearchReader: RoomSearchReader,
    private val jobPostingFinder: JobPostingFinder,
    private val clock: Clock,
) {
    // 결과를 여기서 만들지 않고 Manager 것을 그대로 통과시킨다 — 중복 요청이면 여기서 만든 room 이 아니라
    // 이미 있던 룸이 돌아오기 때문이다(MOI-331). 그 판정은 쓰기 트랜잭션 안에서만 확정된다.
    fun createRoom(hostMemberId: UUID, command: RoomCreationCommand): RoomCreationResult {
        log.debug { "room.create hostMemberId=$hostMemberId" }
        catalogRefValidator.validateJobRoles(listOf(command.jobRoleId))
        (command.meetingPlace as? MeetingPlace.Offline)?.let { catalogRefValidator.validateSigungu(it.sigunguId) }
        // TODO(BE-02B): job_posting 엔티티/리포지토리가 생기면 postingId 존재·활성 검증을 추가한다.

        val resumeFile = resumeValidator.validateOwnedBy(hostMemberId, command.resumeId)

        val room = Room.create(
            // TODO: ERD Step 4는 room id 를 시간 정렬 식별자(UUIDv7)로 둔다. 생성기 도입 시 이 한 줄만 교체.
            id = UUID.randomUUID(),
            jobPostingId = command.jobPostingId,
            jobRoleId = command.jobRoleId,
            title = command.title,
            description = command.description,
            interviewStage = command.interviewStage,
            interviewType = command.interviewType,
            meetingPlace = command.meetingPlace,
            capacity = command.capacity,
            schedule = command.schedule,
            resumeSharingPolicy = command.resumeSharingPolicy,
            now = LocalDateTime.now(clock),
        )
        return roomManager.create(room, hostMemberId, command.resumeId, resumeFile)
    }

    fun updateRoom(memberId: UUID, roomId: UUID, command: RoomUpdateCommand) {
        log.debug { "room.update memberId=$memberId roomId=$roomId" }
        (command.meetingPlace as? MeetingPlace.Offline)?.let { catalogRefValidator.validateSigungu(it.sigunguId) }
        roomManager.update(roomId, memberId, command)
    }

    fun confirmRoom(memberId: UUID, roomId: UUID) {
        log.debug { "room.confirm memberId=$memberId roomId=$roomId" }
        roomManager.confirm(roomId, memberId)
    }

    fun getRoom(roomId: UUID): RoomDetail = roomFinder.getDetail(roomId)

    fun getRoomCreationLimit(memberId: UUID, jobPostingId: Long, jobRoleId: Long): RoomCreationLimit {
        return roomFinder.getCreationLimit(memberId, jobPostingId, jobRoleId)
    }

    fun getRoomSummaries(roomIds: Collection<UUID>): List<RoomSummary> = roomFinder.getSummaries(roomIds)

    fun getRoomSummariesByStatus(roomIds: Collection<UUID>): RoomSummariesByStatus = roomFinder.getSummariesByStatus(roomIds)

    // 좁힌 결과가 비면 조회할 것이 없다 — 빈 IN 목록이 쿼리에 들어가지 않게 여기서 끝낸다.
    fun searchRooms(
        condition: RoomSearchCondition,
        sort: RoomSortOrder,
        cursor: RoomCursor?,
        size: Int,
    ): RoomCardPage {
        val companyPostingIds = condition.companyId?.let { jobPostingFinder.getIdsByCompanyId(it) }
        val jobPostingIds = condition.resolveJobPostingTargets(companyPostingIds)
        if (jobPostingIds != null && jobPostingIds.isEmpty()) return RoomCardPage.EMPTY

        return roomSearchReader.search(condition, jobPostingIds, sort, cursor, size)
    }
}
