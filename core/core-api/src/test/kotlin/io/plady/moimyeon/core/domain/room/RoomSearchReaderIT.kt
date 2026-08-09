package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.JobPostingEntity
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Session
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomSearchReaderIT(
    private val roomService: RoomService,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val jobPostingRepository: JobPostingRepository,
    private val entityManager: EntityManager,
    private val clock: Clock,
) : ContextTest() {
    // Reader 가 실제 Clock 으로 "지금"을 읽으므로 룸 일정은 진짜 미래여야 한다.
    // 대신 이 값을 한 번만 읽어 테스트 안에서는 고정된 기준으로 쓴다.
    private val now: LocalDateTime = LocalDateTime.now(clock)

    @Test
    fun `룸 목록과 인원·대기 수를 한 페이지로 조립한다`() {
        val roomId = saveRoom(startAt = future(1), maxCapacity = 6)
        repeat(2) { join(roomId) }
        repeat(3) { applyPending(roomId) }

        val card = search().cards.single()

        assertThat(card.room.id).isEqualTo(roomId)
        assertThat(card.currentParticipants).isEqualTo(2)
        assertThat(card.pendingApplications).isEqualTo(3)
        assertThat(card.recruitStatus).isEqualTo(RecruitStatus.RECRUITING)
    }

    // 집계 쿼리는 GROUP BY 라 참여·신청이 없는 룸을 아예 돌려주지 않는다. 0 을 채우는 것이 이 계층의 계약이다.
    @Test
    fun `참여나 신청이 없는 룸은 0으로 채운다`() {
        saveRoom(startAt = future(1))

        val card = search().cards.single()

        assertThat(card.currentParticipants).isZero()
        assertThat(card.pendingApplications).isZero()
    }

    @Test
    fun `정원이 찬 룸은 모집 마감으로 계산된다`() {
        val roomId = saveRoom(startAt = future(1), maxCapacity = 2)
        repeat(2) { join(roomId) }

        assertThat(search().cards.single().recruitStatus).isEqualTo(RecruitStatus.CLOSED)
    }

    @Test
    fun `다음 페이지가 있으면 다음 커서를 함께 반환한다`() {
        val first = saveRoom(startAt = future(1))
        saveRoom(startAt = future(2))

        val page = search(size = 1)

        assertThat(page.cards).hasSize(1)
        // 커서는 "여기까지 읽었다"는 지점이므로 이번 페이지의 마지막 행을 가리켜야 한다.
        assertThat(page.nextCursor?.id).isEqualTo(first)
        assertThat(page.nextCursor?.sortValue).isEqualTo(future(1))
    }

    // size + 1 을 읽어 판정하지 않으면 정확히 size 건으로 끝날 때 빈 페이지가 한 번 더 나간다.
    @Test
    fun `남은 룸이 페이지 크기와 정확히 같으면 다음 커서를 주지 않는다`() {
        saveRoom(startAt = future(1))
        saveRoom(startAt = future(2))

        assertThat(search(size = 2).nextCursor).isNull()
    }

    @Test
    fun `전체 건수는 페이지 크기와 무관하게 조회 조건 기준으로 센다`() {
        repeat(3) { saveRoom(startAt = future(it + 1L)) }

        assertThat(search(size = 1).totalCount).isEqualTo(3)
    }

    @Test
    fun `회사로 좁히면 그 회사 공고의 룸만 조회한다`() {
        val companyId = 4001L
        val posting = savePosting(companyId)
        val target = saveRoom(startAt = future(1), jobPostingId = posting)
        saveRoom(startAt = future(2), jobPostingId = savePosting(companyId = 4002L))

        val page = search(condition = RoomSearchCondition.EMPTY.copy(companyId = companyId))

        assertThat(page.cards.map { it.room.id }).containsExactly(target)
        assertThat(page.totalCount).isEqualTo(1)
    }

    // 좁힌 결과가 없으면 조회할 것도 없다. 회사의 공고 목록을 읽는 쿼리 한 번으로 끝나고,
    // 빈 IN 목록이 룸 쿼리에 실려 나가지 않는다(빈 IN 의 처리는 구현체마다 다르므로 의존하지 않는다).
    @Test
    fun `공고가 없는 회사로 좁히면 룸을 조회하지 않고 빈 페이지를 반환한다`() {
        saveRoom(startAt = future(1))

        lateinit var page: RoomCardPage
        val queries = countQueries { page = search(condition = RoomSearchCondition.EMPTY.copy(companyId = 9999L)) }

        assertThat(page.cards).isEmpty()
        assertThat(page.nextCursor).isNull()
        assertThat(page.totalCount).isZero()
        assertThat(queries).isEqualTo(1)
    }

    @Test
    fun `그 회사의 공고가 아닌 공고를 함께 지정하면 빈 페이지를 반환한다`() {
        val companyId = 4003L
        savePosting(companyId)
        val otherPosting = savePosting(companyId = 4004L)
        saveRoom(startAt = future(1), jobPostingId = otherPosting)

        val page = search(
            condition = RoomSearchCondition.EMPTY.copy(companyId = companyId, jobPostingId = otherPosting),
        )

        assertThat(page.cards).isEmpty()
    }

    // 목록·전체 건수·참여 집계·대기 집계 넷이면 충분하다. 룸마다 집계를 부르면 여기서 늘어난다.
    @Test
    fun `룸이 여러 건이어도 쿼리 수가 룸 수에 비례하지 않는다`() {
        repeat(5) { saveRoom(startAt = future(it + 1L)) }

        assertThat(countQueries { search() }).isEqualTo(4)
    }

    private fun countQueries(block: () -> Unit): Long {
        entityManager.flush()
        val statistics = entityManager.unwrap(Session::class.java).sessionFactory.statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()
        block()
        return statistics.prepareStatementCount
    }

    private fun search(
        condition: RoomSearchCondition = RoomSearchCondition.EMPTY,
        sort: RoomSortOrder = RoomSortOrder.SCHEDULE,
        cursor: RoomCursor? = null,
        size: Int = 20,
    ): RoomCardPage = roomService.searchRooms(condition, sort, cursor, size)

    private fun saveRoom(
        startAt: LocalDateTime,
        jobPostingId: Long = 1L,
        maxCapacity: Short = 4,
    ): UUID = roomRepository.saveAndFlush(
        RoomEntity(
            id = UUID.randomUUID(),
            jobPostingId = jobPostingId,
            jobRoleId = 1L,
            sigunguId = null,
            title = "룸 $startAt",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = null,
            meetingType = MeetingType.ONLINE,
            minCapacity = 2,
            maxCapacity = maxCapacity,
            startAt = startAt,
            durationMinutes = 60,
        ),
    ).id

    private var postingSeq = 0

    private fun savePosting(companyId: Long): Long = jobPostingRepository.saveAndFlush(
        JobPostingEntity(
            sourceUid = "room-search-it-${postingSeq++}",
            companyId = companyId,
            title = "공고 $postingSeq",
        ),
    ).id

    private fun join(roomId: UUID) = participationRepository.saveAndFlush(
        ParticipationEntity(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            participationRole = ParticipationRole.PARTICIPANT,
            status = ParticipationStatus.JOINED,
            joinedAt = now,
        ),
    )

    private fun applyPending(roomId: UUID) {
        val applicantMemberId = UUID.randomUUID()
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantMemberId,
                note = "",
                appliedAt = now,
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = applicantMemberId,
            ),
        )
    }

    private fun future(days: Long): LocalDateTime = now.plusDays(days)
}
