package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
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

        val detail = roomFinder.getRoom(roomId)

        assertThat(detail.pendingApplicationCount).isEqualTo(3)
    }

    @Test
    fun `대기 중인 신청이 없으면 대기 신청 수는 0이다`() {
        val detail = roomFinder.getRoom(roomId)

        assertThat(detail.pendingApplicationCount).isEqualTo(0)
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
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "카카오 백엔드 2차 대비",
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
}
