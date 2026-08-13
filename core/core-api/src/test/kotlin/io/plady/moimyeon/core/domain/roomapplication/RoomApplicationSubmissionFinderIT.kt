package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomApplicationSubmissionFinderIT(
    private val finder: RoomApplicationSubmissionFinder,
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
) : ContextTest() {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000436")

    @Test
    fun `처리 대기 신청만 최근 신청 순으로 반환한다`() {
        val recentRoomId = persistRoom("최근 신청한 백엔드 모의면접")
        val olderRoomId = persistRoom("먼저 신청한 백엔드 모의면접")
        val rejectedRoomId = persistRoom("반려된 신청의 백엔드 모의면접")
        val recentApplication = persistApplication(
            recentRoomId,
            RoomApplicationStatus.PENDING,
            LocalDateTime.of(2026, 8, 5, 14, 30),
            "backend.pdf",
        )
        persistApplication(
            olderRoomId,
            RoomApplicationStatus.PENDING,
            LocalDateTime.of(2026, 8, 3, 14, 30),
            "older-backend.pdf",
        )
        persistApplication(
            rejectedRoomId,
            RoomApplicationStatus.REJECTED,
            LocalDateTime.of(2026, 8, 4, 14, 30),
            "rejected.pdf",
        )

        val result = finder.getPendingByApplicant(memberId)

        assertThat(result.map { it.roomId }).containsExactly(recentRoomId, olderRoomId)
        assertThat(result.first().id).isEqualTo(recentApplication.id)
        assertThat(result.first().resumeOriginalName).isEqualTo("backend.pdf")
    }

    @Test
    fun `처리 대기 신청이 없으면 빈 목록을 반환한다`() {
        assertThat(finder.getPendingByApplicant(memberId)).isEmpty()
    }

    @Test
    fun `신청 제출 이력서가 없으면 불완전한 신청을 반환하지 않는다`() {
        val roomId = persistRoom("제출 이력서가 없는 모의면접 신청")
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = memberId,
                note = "신청합니다.",
                appliedAt = LocalDateTime.of(2026, 8, 5, 14, 30),
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = memberId,
            ),
        )

        assertThatThrownBy { finder.getPendingByApplicant(memberId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("제출 이력서")
    }

    private fun persistRoom(title: String): UUID {
        val roomId = UUID.randomUUID()
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = title,
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 5,
                startAt = LocalDateTime.of(2026, 8, 25, 19, 0),
                durationMinutes = 60,
            ),
        )
        return roomId
    }

    private fun persistApplication(
        roomId: UUID,
        status: RoomApplicationStatus,
        appliedAt: LocalDateTime,
        resumeOriginalName: String,
    ): RoomApplicationEntity {
        val application = roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = memberId,
                note = "신청합니다.",
                appliedAt = appliedAt,
                status = status,
                pendingMemberId = memberId.takeIf { status == RoomApplicationStatus.PENDING },
            ),
        )
        resumeSubmissionRepository.saveAndFlush(
            ResumeSubmissionEntity(
                roomApplicationId = application.id,
                roomId = roomId,
                memberId = memberId,
                sourceResumeId = UUID.randomUUID(),
                fileKey = "resumes/$memberId/$resumeOriginalName",
                originalName = resumeOriginalName,
                sizeBytes = 1024L,
                contentType = "application/pdf",
                submittedAt = appliedAt,
            ),
        )
        return application
    }
}
