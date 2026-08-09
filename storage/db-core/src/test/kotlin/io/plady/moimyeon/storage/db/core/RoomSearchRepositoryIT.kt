package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.CoreDbContextTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomSearchRepositoryIT(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val entityManager: EntityManager,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)

    // --- 기본 제외 규칙 -------------------------------------------------------

    @Test
    fun `완료·취소·확정된 룸은 목록에서 제외한다`() {
        val recruiting = saveRoom(startAt = future(1))
        forceStatus(saveRoom(startAt = future(2)), RoomStatus.CONFIRMED)
        forceStatus(saveRoom(startAt = future(3)), RoomStatus.COMPLETED)
        forceStatus(saveRoom(startAt = future(4)), RoomStatus.CANCELED)

        assertThat(searchBySchedule()).containsExactly(recruiting)
    }

    @Test
    fun `삭제된 룸은 목록에서 제외한다`() {
        val kept = saveRoom(startAt = future(1))
        val deleted = roomRepository.findById(saveRoom(startAt = future(2))).get()
        deleted.delete(now)
        entityManager.flush()

        assertThat(searchBySchedule()).containsExactly(kept)
    }

    // 신청 자격도 start_at > now 라(RoomValidator) 목록과 같은 술어를 쓴다.
    // 정확히 now 인 룸을 노출하면 "보이는데 신청은 막히는" 한 순간이 생긴다.
    @Test
    fun `시작 일정이 지났거나 지금인 룸은 목록에서 제외한다`() {
        val upcoming = saveRoom(startAt = now.plusSeconds(1))
        saveRoom(startAt = now)
        saveRoom(startAt = now.minusSeconds(1))

        assertThat(searchBySchedule()).containsExactly(upcoming)
    }

    // --- 정렬과 커서 ----------------------------------------------------------

    @Test
    fun `일정 빠른 순으로 정렬한다`() {
        val late = saveRoom(startAt = future(3))
        val early = saveRoom(startAt = future(1))
        val middle = saveRoom(startAt = future(2))

        assertThat(searchBySchedule()).containsExactly(early, middle, late)
    }

    @Test
    fun `최근 생성 순으로 정렬한다`() {
        val oldest = saveRoom(startAt = future(1)).also { forceCreatedAt(it, day(1)) }
        val newest = saveRoom(startAt = future(2)).also { forceCreatedAt(it, day(3)) }
        val middle = saveRoom(startAt = future(3)).also { forceCreatedAt(it, day(2)) }

        assertThat(searchByRecent()).containsExactly(newest, middle, oldest)
    }

    @Test
    fun `커서 이후의 룸만 조회한다`() {
        val first = saveRoom(startAt = future(1))
        val second = saveRoom(startAt = future(2))
        val third = saveRoom(startAt = future(3))

        assertThat(searchBySchedule(size = 2)).containsExactly(first, second)
        assertThat(searchBySchedule(size = 2, cursorSortValue = future(2), cursorId = second)).containsExactly(third)
    }

    // 이 이슈의 핵심 위험. 정렬 키만 비교하면 여기서 행이 통째로 사라지거나 무한 반복한다.
    @Test
    fun `정렬 키가 같은 룸이 페이지 크기를 넘어도 누락되지 않는다`() {
        val sameStartAt = future(1)
        val ids = (1..5).map { saveRoom(startAt = sameStartAt) }

        val visited = walkAll(size = 2) { cursor ->
            searchBySchedule(size = 2, cursorSortValue = cursor?.let { sameStartAt }, cursorId = cursor)
        }

        assertThat(visited).containsExactlyInAnyOrderElementsOf(ids)
        assertThat(visited).doesNotHaveDuplicates()
    }

    // 쿼리가 두 벌이라 한쪽만 고치는 실수가 난다. 내림차순 커서도 같은 보증을 받는지 따로 본다.
    @Test
    fun `최근 생성 순에서도 정렬 키가 같은 룸이 페이지 크기를 넘어도 누락되지 않는다`() {
        val sameCreatedAt = day(1)
        val ids = (1..5).map { saveRoom(startAt = future(it.toLong())).also { id -> forceCreatedAt(id, sameCreatedAt) } }

        val visited = walkAll(size = 2) { cursor ->
            searchByRecent(size = 2, cursorSortValue = cursor?.let { sameCreatedAt }, cursorId = cursor)
        }

        assertThat(visited).containsExactlyInAnyOrderElementsOf(ids)
        assertThat(visited).doesNotHaveDuplicates()
    }

    @Test
    fun `마지막 룸의 커서로 조회하면 결과가 비어 있다`() {
        saveRoom(startAt = future(1))
        val last = saveRoom(startAt = future(2))

        assertThat(searchBySchedule(cursorSortValue = future(2), cursorId = last)).isEmpty()
    }

    @Test
    fun `전체 건수는 커서와 무관하게 조회 조건 기준으로 센다`() {
        val first = saveRoom(startAt = future(1))
        saveRoom(startAt = future(2))
        forceStatus(saveRoom(startAt = future(3)), RoomStatus.CANCELED)

        searchBySchedule(size = 1, cursorSortValue = future(1), cursorId = first)

        assertThat(count()).isEqualTo(2)
    }

    // --- 필터 ----------------------------------------------------------------

    // 회사 필터도 이 파라미터로 들어온다. 회사 → 공고 id 변환은 호출자(Reader)의 일이다.
    @Test
    fun `채용 공고로 좁혀 조회한다`() {
        val target = saveRoom(startAt = future(1), jobPostingId = 11L)
        saveRoom(startAt = future(2), jobPostingId = 22L)

        assertThat(searchBySchedule(jobPostingIds = listOf(11L))).containsExactly(target)
    }

    @Test
    fun `직무로 좁혀 조회한다`() {
        val target = saveRoom(startAt = future(1), jobRoleId = 7L)
        saveRoom(startAt = future(2), jobRoleId = 8L)

        assertThat(searchBySchedule(jobRoleId = 7L)).containsExactly(target)
    }

    @Test
    fun `면접 단계로 좁혀 조회한다`() {
        val target = saveRoom(startAt = future(1), interviewStage = InterviewStage.SECOND)
        saveRoom(startAt = future(2), interviewStage = InterviewStage.FIRST)

        assertThat(searchBySchedule(interviewStage = InterviewStage.SECOND)).containsExactly(target)
    }

    @Test
    fun `진행 방식으로 좁혀 조회한다`() {
        val target = saveRoom(startAt = future(1), meetingType = MeetingType.OFFLINE, sigunguId = 1L)
        saveRoom(startAt = future(2), meetingType = MeetingType.ONLINE)

        assertThat(searchBySchedule(meetingType = MeetingType.OFFLINE)).containsExactly(target)
    }

    @Test
    fun `지역으로 좁혀 조회한다`() {
        val target = saveRoom(startAt = future(1), meetingType = MeetingType.OFFLINE, sigunguId = 1L)
        saveRoom(startAt = future(2), meetingType = MeetingType.OFFLINE, sigunguId = 2L)
        saveRoom(startAt = future(3), meetingType = MeetingType.ONLINE)

        assertThat(searchBySchedule(sigunguId = 1L)).containsExactly(target)
    }

    @Test
    fun `일정 범위로 좁혀 조회한다`() {
        saveRoom(startAt = future(1))
        val inRange = saveRoom(startAt = future(5))
        saveRoom(startAt = future(9))

        assertThat(searchBySchedule(startFrom = future(3), startTo = future(7))).containsExactly(inRange)
        assertThat(searchBySchedule(startFrom = future(5), startTo = future(5))).containsExactly(inRange)
    }

    @Test
    fun `자리가 남은 룸만 조회한다`() {
        val open = saveRoom(startAt = future(1), maxCapacity = 4)
        val full = saveRoom(startAt = future(2), maxCapacity = 2)
        repeat(1) { join(open) }
        repeat(2) { join(full) }

        assertThat(searchBySchedule(availableOnly = true)).containsExactly(open)
    }

    // "자리 남음"의 기준은 정원 확정(RoomApplicationManager)과 같은 활성 참여 수여야 한다.
    // 두 기준이 갈리면 목록에서 자리 있다고 보고 신청했는데 수락 단계에서 정원 초과가 난다.
    // 퇴장(status=LEFT)은 아직 만들어지는 경로가 없어 여기서도 빼지 않는다 — 생기면 정원 판정 세 곳을 함께 고친다.
    @Test
    fun `삭제된 참여는 자리 계산에서 제외한다`() {
        val room = saveRoom(startAt = future(1), maxCapacity = 2)
        join(room)
        softDelete(join(room))

        assertThat(searchBySchedule(availableOnly = true)).containsExactly(room)
    }

    // 정원이 찬 룸도 기본 목록에는 나온다 — 확정 전이면 대기 신청이 가능하기 때문이다(「룸 참여」 §4.9).
    @Test
    fun `정원이 찬 룸도 기본 목록에는 포함한다`() {
        val full = saveRoom(startAt = future(1), maxCapacity = 2)
        repeat(2) { join(full) }

        assertThat(searchBySchedule()).containsExactly(full)
    }

    @Test
    fun `서로 다른 조건은 함께 만족하는 룸만 조회한다`() {
        val target = saveRoom(startAt = future(1), jobRoleId = 7L, interviewStage = InterviewStage.SECOND)
        saveRoom(startAt = future(2), jobRoleId = 7L, interviewStage = InterviewStage.FIRST)
        saveRoom(startAt = future(3), jobRoleId = 8L, interviewStage = InterviewStage.SECOND)

        val found = searchBySchedule(jobRoleId = 7L, interviewStage = InterviewStage.SECOND)

        assertThat(found).containsExactly(target)
    }

    @Test
    fun `필터를 적용해도 완료·취소·일정 경과 룸은 제외된다`() {
        val kept = saveRoom(startAt = future(1), jobRoleId = 7L)
        forceStatus(saveRoom(startAt = future(2), jobRoleId = 7L), RoomStatus.CANCELED)
        saveRoom(startAt = now.minusDays(1), jobRoleId = 7L)

        assertThat(searchBySchedule(jobRoleId = 7L)).containsExactly(kept)
    }

    @Test
    fun `필터를 걸어도 커서 순회에 중복이나 누락이 없다`() {
        val sameStartAt = future(1)
        val ids = (1..5).map { saveRoom(startAt = sameStartAt, jobRoleId = 7L) }
        (1..3).forEach { saveRoom(startAt = sameStartAt, jobRoleId = 8L) }

        val visited = walkAll(size = 2) { cursor ->
            searchBySchedule(
                size = 2,
                jobRoleId = 7L,
                cursorSortValue = cursor?.let { sameStartAt },
                cursorId = cursor,
            )
        }

        assertThat(visited).containsExactlyInAnyOrderElementsOf(ids)
        assertThat(visited).doesNotHaveDuplicates()
    }

    @Test
    fun `전체 건수도 필터 조건을 반영한다`() {
        saveRoom(startAt = future(1), jobRoleId = 7L)
        saveRoom(startAt = future(2), jobRoleId = 7L)
        saveRoom(startAt = future(3), jobRoleId = 8L)

        assertThat(count(jobRoleId = 7L)).isEqualTo(2)
        assertThat(count()).isEqualTo(3)
    }

    // --- 헬퍼 ----------------------------------------------------------------

    private fun searchBySchedule(
        size: Int = 20,
        jobPostingIds: Collection<Long>? = null,
        jobRoleId: Long? = null,
        interviewStage: InterviewStage? = null,
        meetingType: MeetingType? = null,
        sigunguId: Long? = null,
        startFrom: LocalDateTime? = null,
        startTo: LocalDateTime? = null,
        availableOnly: Boolean = false,
        cursorSortValue: LocalDateTime? = null,
        cursorId: UUID? = null,
    ): List<UUID> = roomRepository.searchBySchedule(
        now, jobPostingIds, jobRoleId, interviewStage, meetingType, sigunguId,
        startFrom, startTo, availableOnly, cursorSortValue, cursorId, PageRequest.of(0, size),
    ).map { it.id }

    private fun searchByRecent(
        size: Int = 20,
        cursorSortValue: LocalDateTime? = null,
        cursorId: UUID? = null,
    ): List<UUID> = roomRepository.searchByRecent(
        now, null, null, null, null, null,
        null, null, false, cursorSortValue, cursorId, PageRequest.of(0, size),
    ).map { it.id }

    private fun count(jobRoleId: Long? = null): Long = roomRepository.countSearchTargets(now, null, jobRoleId, null, null, null, null, null, false)

    // 커서를 따라 끝까지 순회한다. 상한은 무한 루프 방지용이고, 걸리면 커서가 전진하지 않는다는 뜻이다.
    private fun walkAll(size: Int, page: (UUID?) -> List<UUID>): List<UUID> {
        val visited = mutableListOf<UUID>()
        var cursor: UUID? = null
        repeat(MAX_PAGES) {
            val rows = page(cursor)
            if (rows.isEmpty()) return visited
            visited += rows
            cursor = rows.last()
            if (rows.size < size) return visited
        }
        error("커서가 $MAX_PAGES 페이지 안에 끝나지 않았다. 커서가 전진하지 않는다.")
    }

    private fun saveRoom(
        startAt: LocalDateTime,
        jobPostingId: Long = 1L,
        jobRoleId: Long = 1L,
        interviewStage: InterviewStage = InterviewStage.FIRST,
        meetingType: MeetingType = MeetingType.ONLINE,
        sigunguId: Long? = null,
        maxCapacity: Short = 4,
    ): UUID {
        val room = RoomEntity(
            id = UUID.randomUUID(),
            jobPostingId = jobPostingId,
            jobRoleId = jobRoleId,
            sigunguId = sigunguId,
            title = "룸 $startAt",
            description = null,
            interviewStage = interviewStage,
            interviewType = null,
            meetingType = meetingType,
            minCapacity = 2,
            maxCapacity = maxCapacity,
            startAt = startAt,
            durationMinutes = 60,
        )
        return roomRepository.saveAndFlush(room).id
    }

    private fun join(roomId: UUID): ParticipationEntity = participationRepository.saveAndFlush(
        ParticipationEntity(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            participationRole = ParticipationRole.PARTICIPANT,
            status = ParticipationStatus.JOINED,
            joinedAt = now,
        ),
    )

    private fun softDelete(participation: ParticipationEntity) {
        participation.delete(now)
        entityManager.flush()
    }

    // status 는 protected set 이고 상태 전이 메서드가 아직 없다(확정·완료는 별도 이슈).
    // created_at 은 @CreationTimestamp 라 저장 시각이 박힌다.
    // 둘 다 앱 경로로는 만들 수 없는 상태라 벌크 업데이트로 만들고, 영속성 컨텍스트를 비워 재조회하게 한다.
    private fun forceStatus(roomId: UUID, status: RoomStatus) = forceUpdate("r.status = :value", roomId, status)

    private fun forceCreatedAt(roomId: UUID, createdAt: LocalDateTime) = forceUpdate("r.createdAt = :value", roomId, createdAt)

    private fun forceUpdate(assignment: String, roomId: UUID, value: Any) {
        entityManager.flush()
        entityManager.createQuery("UPDATE RoomEntity r SET $assignment WHERE r.id = :id")
            .setParameter("value", value)
            .setParameter("id", roomId)
            .executeUpdate()
        entityManager.clear()
    }

    private fun future(days: Long): LocalDateTime = now.plusDays(days)

    private fun day(days: Long): LocalDateTime = now.minusDays(30).plusDays(days)

    companion object {
        private const val MAX_PAGES = 20
    }
}
