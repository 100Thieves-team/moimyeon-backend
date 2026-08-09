package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.MeetingType
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
    private val entityManager: EntityManager,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)

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

        val page1 = searchBySchedule(size = 2)
        assertThat(page1).containsExactly(first, second)

        val page2 = searchBySchedule(size = 2, cursorSortValue = future(2), cursorId = second)
        assertThat(page2).containsExactly(third)
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

        assertThat(roomRepository.countSearchTargets(now)).isEqualTo(2)
    }

    private fun searchBySchedule(
        size: Int = 20,
        cursorSortValue: LocalDateTime? = null,
        cursorId: UUID? = null,
    ): List<UUID> = roomRepository.searchBySchedule(now, cursorSortValue, cursorId, PageRequest.of(0, size)).map { it.id }

    private fun searchByRecent(
        size: Int = 20,
        cursorSortValue: LocalDateTime? = null,
        cursorId: UUID? = null,
    ): List<UUID> = roomRepository.searchByRecent(now, cursorSortValue, cursorId, PageRequest.of(0, size)).map { it.id }

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

    private fun saveRoom(startAt: LocalDateTime): UUID {
        val room = RoomEntity(
            id = UUID.randomUUID(),
            jobPostingId = 1L,
            jobRoleId = 1L,
            sigunguId = null,
            title = "룸 $startAt",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = null,
            meetingType = MeetingType.ONLINE,
            minCapacity = 2,
            maxCapacity = 4,
            startAt = startAt,
            durationMinutes = 60,
        )
        return roomRepository.saveAndFlush(room).id
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
