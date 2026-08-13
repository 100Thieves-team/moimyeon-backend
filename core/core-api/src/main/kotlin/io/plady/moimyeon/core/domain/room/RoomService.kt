package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.domain.jobposting.JobPostingFinder
import io.plady.moimyeon.core.domain.resume.ResumeValidator
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

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
    // 방(룸) 생성. 카탈로그·이력서 참조 검증은 쓰기 트랜잭션 밖에서(profile 패턴과 동일),
    // 실제 등록은 RoomManager 트랜잭션에서 한다.
    //
    // 결과를 여기서 만들지 않고 Manager 것을 그대로 통과시킨다 — 중복 요청이면 여기서 만든 room 이 아니라
    // 이미 있던 룸이 돌아오기 때문이다(MOI-331). 그 판정은 쓰기 트랜잭션 안에서만 확정된다.
    fun createRoom(hostMemberId: UUID, command: RoomCreationCommand): RoomCreationResult {
        catalogRefValidator.validateJobRoles(listOf(command.jobRoleId))
        (command.meetingPlace as? MeetingPlace.Offline)?.let { catalogRefValidator.validateSigungu(it.sigunguId) }
        // TODO(BE-02B): job_posting 엔티티/리포지토리가 생기면 postingId 존재·활성 검증을 추가한다.

        // 소유권·존재·미삭제를 한 번에 보고 제출로 복사할 파일 정보를 돌려준다. 신청 경로와 같은 도구다.
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

    // 방(룸) 수정. 방장 검증은 RoomManager 에서(participation 기반). 오프라인이면 지역 참조를 검증한다.
    fun updateRoom(memberId: UUID, roomId: UUID, command: RoomUpdateCommand) {
        (command.meetingPlace as? MeetingPlace.Offline)?.let { catalogRefValidator.validateSigungu(it.sigunguId) }
        roomManager.update(roomId, memberId, command)
    }

    // 룸 취소. 조건 판정(모집 중인가, 참여자가 남았는가)은 전부 RoomManager 가 자기 트랜잭션 안에서 한다.
    fun cancelRoom(memberId: UUID, roomId: UUID) {
        roomManager.cancel(roomId, memberId)
    }

    // 진행 확정. 조건 판정은 RoomConfirmation 이 소유하고 RoomManager 가 락 안에서 부른다.
    fun confirmRoom(memberId: UUID, roomId: UUID) {
        roomManager.confirm(roomId, memberId)
    }

    // 룸 단건 조회(읽기). RoomFinder 가 룸 + 현재 인원 + 방장을 모아 온다.
    fun getRoom(roomId: UUID): RoomDetail = roomFinder.getDetail(roomId)

    // 중복 생성 제한 조회(읽기, §4.7). 생성 화면이 경고를 띄울지 판단하는 데 쓴다.
    fun getRoomCreationLimit(memberId: UUID, jobPostingId: Long, jobRoleId: Long): RoomCreationLimit {
        return roomFinder.getCreationLimit(memberId, jobPostingId, jobRoleId)
    }

    fun getRoomSummaries(roomIds: Collection<UUID>): List<RoomSummary> = roomFinder.getSummaries(roomIds)

    fun getRoomSummariesByStatus(roomIds: Collection<UUID>): RoomSummariesByStatus = roomFinder.getSummariesByStatus(roomIds)

    // 룸 탐색 목록(읽기). 룸은 회사를 직접 알지 못하므로(room → job_posting → company)
    // 회사 필터만 공고 id 목록으로 바꿔 넘기고, 나머지는 Reader 가 그대로 조회한다.
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
