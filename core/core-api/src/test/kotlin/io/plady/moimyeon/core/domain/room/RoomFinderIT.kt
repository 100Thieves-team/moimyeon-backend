package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomFinderIT(
    private val roomFinder: RoomFinder,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val startAt = LocalDateTime.of(2026, 9, 1, 19, 0)
    private val appliedAt = LocalDateTime.of(2026, 8, 1, 10, 0)

    @BeforeEach
    fun setUp() {
        persistRoom()
        persistHostParticipation()
    }

    // 대기 수는 처리되지 않은 신청만 센다. 처리된 신청까지 세면 화면의 "신청 대기 N" 이 부풀고,
    // F2 확인 모달의 "대기 신청 N건 자동 정리" 고지도 함께 틀어진다.
    @Test
    fun `대기 신청 수는 처리되지 않은 신청만 센다`() {
        repeat(3) { persistApplication(RoomApplicationStatus.PENDING) }
        listOf(
            RoomApplicationStatus.ACCEPTED,
            RoomApplicationStatus.REJECTED,
            RoomApplicationStatus.WITHDRAWN,
            RoomApplicationStatus.ROOM_CANCELED,
            RoomApplicationStatus.ROOM_CONFIRMED,
        ).forEach { persistApplication(it) }

        val detail = roomFinder.getDetail(roomId)

        assertThat(detail.pendingApplicationCount).isEqualTo(3)
    }

    @Test
    fun `대기 중인 신청이 없으면 대기 신청 수는 0이다`() {
        val detail = roomFinder.getDetail(roomId)

        assertThat(detail.pendingApplicationCount).isEqualTo(0)
    }

    @Test
    fun `참여 룸 요약은 진행 상태와 완료 상태로 구분하고 일정 순으로 정렬한다`() {
        val confirmedRoomId = persistRoom(
            title = "확정된 백엔드 모의면접",
            status = RoomStatus.CONFIRMED,
            startAt = LocalDateTime.of(2026, 8, 18, 19, 0),
        )
        persistParticipation(confirmedRoomId)
        persistParticipation(confirmedRoomId)

        val recruitingRoomId = persistRoom(
            title = "모집 중인 백엔드 모의면접",
            status = RoomStatus.RECRUITING,
            startAt = LocalDateTime.of(2026, 8, 19, 19, 0),
        )
        persistParticipation(recruitingRoomId)

        val olderCompletedRoomId = persistRoom(
            title = "먼저 완료된 백엔드 모의면접",
            status = RoomStatus.COMPLETED,
            startAt = LocalDateTime.of(2026, 8, 8, 19, 0),
        )
        val newerCompletedRoomId = persistRoom(
            title = "나중에 완료된 백엔드 모의면접",
            status = RoomStatus.COMPLETED,
            startAt = LocalDateTime.of(2026, 8, 10, 19, 0),
        )
        val canceledRoomId = persistRoom(
            title = "취소된 백엔드 모의면접",
            status = RoomStatus.CANCELED,
            startAt = LocalDateTime.of(2026, 8, 20, 19, 0),
        )

        val result = roomFinder.getSummariesByStatus(
            listOf(canceledRoomId, olderCompletedRoomId, recruitingRoomId, newerCompletedRoomId, confirmedRoomId),
        )

        assertThat(result.active.map { it.room.id }).containsExactly(confirmedRoomId, recruitingRoomId)
        assertThat(result.active.map { it.participantCount }).containsExactly(2, 1)
        assertThat(result.completed.map { it.room.id }).containsExactly(newerCompletedRoomId, olderCompletedRoomId)
    }

    private fun persistApplication(status: RoomApplicationStatus) {
        val applicantMemberId = UUID.randomUUID()
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantMemberId,
                note = "잘 부탁드립니다",
                appliedAt = appliedAt,
                status = status,
                pendingMemberId = applicantMemberId.takeIf { status == RoomApplicationStatus.PENDING },
            ),
        )
    }

    private fun persistRoom() {
        persistRoom(
            roomId = roomId,
            title = "카카오 백엔드 2차 대비",
            status = RoomStatus.RECRUITING,
            startAt = startAt,
        )
    }

    private fun persistRoom(
        title: String,
        status: RoomStatus,
        startAt: LocalDateTime,
        roomId: UUID = UUID.randomUUID(),
    ): UUID {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = title,
                description = null,
                interviewStage = InterviewStage.SECOND,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 3,
                maxCapacity = 5,
                startAt = startAt,
                durationMinutes = 90,
            ),
        )
        if (status != RoomStatus.RECRUITING) {
            entityManager.createNativeQuery("update room set status = :status where id = :roomId")
                .setParameter("status", status.name)
                .setParameter("roomId", roomId)
                .executeUpdate()
            entityManager.clear()
        }
        return roomId
    }

    private fun persistHostParticipation() {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = appliedAt,
            ),
        )
    }

    private fun persistParticipation(roomId: UUID) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = UUID.randomUUID(),
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = appliedAt,
            ),
        )
    }
}
