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
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// 같은 (방장, 공고, 직무) 활성 룸 개수(MOI-330). 방장이 room 이 아니라 participation 에 있어
// 조인 쿼리가 되는 자리라 가장 잘 틀린다 — 두 테이블의 필터가 각각 걸리는지 여기서 본다.
//
// "활성"이 어느 상태 집합인지는 core-api 의 ActiveRoomLimit 이 소유한다. 여기서는 그 집합을
// 인자로 받아 쿼리가 IN 을 제대로 거는지만 본다.
@Transactional
class ActiveRoomCountRepositoryIT(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val entityManager: EntityManager,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 13, 12, 0)
    private val host: UUID = UUID.randomUUID()

    // --- 룸 상태 -------------------------------------------------------------

    @Test
    fun `모집 중과 확정과 진행 중인 룸을 센다`() {
        hostRoom()
        hostRoom(status = RoomStatus.CONFIRMED)
        hostRoom(status = RoomStatus.IN_PROGRESS)

        assertThat(count()).isEqualTo(3)
    }

    @Test
    fun `취소된 룸은 세지 않는다`() {
        hostRoom()
        hostRoom(status = RoomStatus.CANCELED)

        assertThat(count()).isEqualTo(1)
    }

    // COMPLETED 로 가는 전이는 아직 없다(MOI-431). 배치가 켜지는 날 한도가 풀려야 하므로
    // 상태를 직접 만들어 술어만 확인한다.
    @Test
    fun `완료된 룸은 세지 않는다`() {
        hostRoom()
        hostRoom(status = RoomStatus.COMPLETED)

        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `운영이 내린 룸은 세지 않는다`() {
        hostRoom()
        val removed = roomRepository.findById(hostRoom()).get()
        removed.delete(now)
        entityManager.flush()

        assertThat(count()).isEqualTo(1)
    }

    // --- 키 축 ---------------------------------------------------------------

    @Test
    fun `공고가 다르면 같은 직무여도 세지 않는다`() {
        hostRoom()
        hostRoom(jobPostingId = OTHER_JOB_POSTING_ID)

        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `직무가 다르면 같은 공고여도 세지 않는다`() {
        hostRoom()
        hostRoom(jobRoleId = OTHER_JOB_ROLE_ID)

        assertThat(count()).isEqualTo(1)
    }

    // 면접 차수·일정이 달라도 키가 아니다. 키는 (방장, 공고, 직무) 셋뿐이다.
    @Test
    fun `일정이 달라도 같은 공고와 직무면 센다`() {
        hostRoom(startAt = now.plusDays(1))
        hostRoom(startAt = now.plusDays(30))

        assertThat(count()).isEqualTo(2)
    }

    // --- 방장 축 -------------------------------------------------------------

    @Test
    fun `다른 회원이 방장인 룸은 세지 않는다`() {
        hostRoom()
        hostRoom(memberId = UUID.randomUUID())

        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `참여자로 들어간 룸은 세지 않는다`() {
        hostRoom()
        hostRoom(role = ParticipationRole.PARTICIPANT)

        assertThat(count()).isEqualTo(1)
    }

    // 자동 위임(MOI-397)이 들어오면 전 방장의 HOST + LEFT 행이 남는다.
    // 상태를 함께 보지 않으면 넘기고 나간 사람이 한도를 계속 문다.
    @Test
    fun `방장을 넘기고 나간 룸은 세지 않는다`() {
        hostRoom()
        hostRoom(participationStatus = ParticipationStatus.LEFT)

        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `삭제된 방장 참여 행은 세지 않는다`() {
        hostRoom()
        val removed = participationRepository.findById(hostParticipationId(hostRoom())).get()
        removed.delete(now)
        entityManager.flush()

        assertThat(count()).isEqualTo(1)
    }

    // --- 헬퍼 ----------------------------------------------------------------

    private fun count(
        memberId: UUID = host,
        jobPostingId: Long = JOB_POSTING_ID,
        jobRoleId: Long = JOB_ROLE_ID,
    ): Long = roomRepository.countActiveHostedRooms(memberId, jobPostingId, jobRoleId, ACTIVE_STATUSES)

    // 룸 + 그 룸의 방장 참여 행을 함께 만든다. 둘이 갖춰져야 카운트 대상이 된다.
    private fun hostRoom(
        memberId: UUID = host,
        jobPostingId: Long = JOB_POSTING_ID,
        jobRoleId: Long = JOB_ROLE_ID,
        status: RoomStatus = RoomStatus.RECRUITING,
        startAt: LocalDateTime = now.plusDays(7),
        role: ParticipationRole = ParticipationRole.HOST,
        participationStatus: ParticipationStatus = ParticipationStatus.JOINED,
    ): UUID {
        val roomId = roomRepository.saveAndFlush(
            RoomEntity(
                id = UUID.randomUUID(),
                jobPostingId = jobPostingId,
                jobRoleId = jobRoleId,
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
            ),
        ).id
        if (status != RoomStatus.RECRUITING) forceStatus(roomId, status)
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = role,
                status = participationStatus,
                joinedAt = now,
            ),
        )
        return roomId
    }

    private fun hostParticipationId(roomId: UUID): Long = participationRepository
        .findFirstByRoomIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
            roomId,
            ParticipationRole.HOST,
            ParticipationStatus.JOINED,
        )!!.id

    // status 는 protected set 이고 CANCELED·COMPLETED 로 가는 앱 경로가 여기에 없다.
    private fun forceStatus(roomId: UUID, status: RoomStatus) {
        entityManager.flush()
        entityManager.createQuery("UPDATE RoomEntity r SET r.status = :status WHERE r.id = :id")
            .setParameter("status", status)
            .setParameter("id", roomId)
            .executeUpdate()
        entityManager.clear()
    }

    companion object {
        private const val JOB_POSTING_ID = 1L
        private const val OTHER_JOB_POSTING_ID = 2L
        private const val JOB_ROLE_ID = 1L
        private const val OTHER_JOB_ROLE_ID = 2L
        private val ACTIVE_STATUSES = setOf(RoomStatus.RECRUITING, RoomStatus.CONFIRMED, RoomStatus.IN_PROGRESS)
    }
}
