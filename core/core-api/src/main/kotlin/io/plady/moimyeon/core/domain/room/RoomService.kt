package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.domain.jobposting.JobPostingFinder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class RoomService(
    private val catalogRefValidator: CatalogRefValidator,
    private val roomManager: RoomManager,
    private val roomFinder: RoomFinder,
    private val roomSearchReader: RoomSearchReader,
    private val jobPostingFinder: JobPostingFinder,
) {
    // 방(룸) 생성. 카탈로그 참조 검증은 쓰기 트랜잭션 밖에서(profile 패턴과 동일),
    // 실제 등록은 RoomManager 트랜잭션에서 한다.
    fun createRoom(hostMemberId: UUID, command: RoomCreationCommand): Room {
        catalogRefValidator.validateJobRoles(listOf(command.jobRoleId))
        (command.meetingPlace as? MeetingPlace.Offline)?.let { catalogRefValidator.validateSigungu(it.sigunguId) }
        // TODO(BE-02B): job_posting 엔티티/리포지토리가 생기면 postingId 존재·활성 검증을 추가한다.

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
            now = LocalDateTime.now(),
        )
        roomManager.create(room, hostMemberId, command.resumeId)
        return room
    }

    // 방(룸) 수정. 방장 검증은 RoomManager 에서(participation 기반). 오프라인이면 지역 참조를 검증한다.
    fun updateRoom(memberId: UUID, roomId: UUID, command: RoomUpdateCommand) {
        (command.meetingPlace as? MeetingPlace.Offline)?.let { catalogRefValidator.validateSigungu(it.sigunguId) }
        roomManager.update(roomId, memberId, command)
    }

    // 방(룸) 삭제. 방장만 가능(RoomManager 에서 검증). 그 외 조건 검사는 두지 않는다(요청 범위).
    fun deleteRoom(memberId: UUID, roomId: UUID) {
        roomManager.delete(roomId, memberId)
    }

    // 룸 단건 조회(읽기). RoomFinder 가 룸 + 현재 인원 + 방장을 모아 온다.
    fun getRoom(roomId: UUID): RoomDetail = roomFinder.getDetail(roomId)

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
