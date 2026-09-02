package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// 발급 게이트가 하나라도 빠지면 곧 개인정보(이력서 원본) 유출이다. 창·정책은 Room 단위 테스트가
// 정밀 커버하므로 여기서는 각 게이트가 실제로 막는지(E1429 번역·룸 소속·회수)를 본다.
@Transactional
class ResumeOriginalViewFinderIT(
    private val resumeOriginalViewFinder: ResumeOriginalViewFinder,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val submitterMemberId = UUID.randomUUID()
    private val submittedAt = LocalDateTime.of(2026, 8, 5, 12, 0)
    private val startAt = LocalDateTime.of(2026, 9, 1, 19, 0)

    @Test
    fun `원본 공개 확정 룸의 제출 이력서 파일을 돌려준다`() {
        val roomId = persistRoom(resumePublic = true, status = RoomStatus.CONFIRMED)
        persistParticipation(roomId, submitterMemberId)
        val submissionId = persistSubmission(roomId, submitterMemberId)

        val file = resumeOriginalViewFinder.getViewableFile(roomId, submissionId)

        assertThat(file.key).isEqualTo("resumes/$submitterMemberId/backend.pdf")
        assertThat(file.contentType).isEqualTo("application/pdf")
    }

    @Test
    fun `확정 전이거나 종료된 룸이면 E1429 를 던진다`() {
        listOf(RoomStatus.RECRUITING, RoomStatus.COMPLETED).forEach { status ->
            val roomId = persistRoom(resumePublic = true, status = status)
            persistParticipation(roomId, submitterMemberId)
            val submissionId = persistSubmission(roomId, submitterMemberId)

            assertNotViewable(roomId, submissionId)
        }
    }

    @Test
    fun `원본 비공개 룸이면 E1429 를 던진다`() {
        val roomId = persistRoom(resumePublic = false, status = RoomStatus.CONFIRMED)
        persistParticipation(roomId, submitterMemberId)
        val submissionId = persistSubmission(roomId, submitterMemberId)

        assertNotViewable(roomId, submissionId)
    }

    @Test
    fun `다른 룸의 제출 id 는 E1010 을 던진다`() {
        val roomId = persistRoom(resumePublic = true, status = RoomStatus.CONFIRMED)
        val otherRoomId = persistRoom(resumePublic = true, status = RoomStatus.CONFIRMED)
        persistParticipation(otherRoomId, submitterMemberId)
        val otherRoomSubmissionId = persistSubmission(otherRoomId, submitterMemberId)

        assertThatThrownBy { resumeOriginalViewFinder.getViewableFile(roomId, otherRoomSubmissionId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
            }
    }

    @Test
    fun `제출자가 룸을 나갔으면 E1429 를 던진다`() {
        val roomId = persistRoom(resumePublic = true, status = RoomStatus.CONFIRMED)
        persistParticipation(roomId, submitterMemberId, status = ParticipationStatus.LEFT)
        val submissionId = persistSubmission(roomId, submitterMemberId)

        assertNotViewable(roomId, submissionId)
    }

    @Test
    fun `없는 룸이면 E1405 를 던진다`() {
        assertThatThrownBy { resumeOriginalViewFinder.getViewableFile(UUID.randomUUID(), 1L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_FOUND)
            }
    }

    private fun assertNotViewable(roomId: UUID, submissionId: Long) {
        assertThatThrownBy { resumeOriginalViewFinder.getViewableFile(roomId, submissionId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_ORIGINAL_NOT_VIEWABLE)
            }
    }

    // --- 픽스처 -------------------------------------------------------------------

    private fun persistRoom(resumePublic: Boolean, status: RoomStatus): UUID {
        val roomId = UUID.randomUUID()
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = resumePublic,
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
        if (status != RoomStatus.RECRUITING) {
            entityManager.createNativeQuery("update room set status = :status where id = :roomId")
                .setParameter("status", status.name)
                .setParameter("roomId", roomId)
                .executeUpdate()
            entityManager.clear()
        }
        return roomId
    }

    private fun persistParticipation(
        roomId: UUID,
        memberId: UUID,
        status: ParticipationStatus = ParticipationStatus.JOINED,
    ) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = status,
                joinedAt = submittedAt,
                leftAt = submittedAt.plusHours(1).takeIf { status == ParticipationStatus.LEFT },
                leftByMemberId = memberId.takeIf { status == ParticipationStatus.LEFT },
            ),
        )
    }

    private fun persistSubmission(roomId: UUID, memberId: UUID): Long {
        val application = roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = memberId,
                note = "",
                appliedAt = submittedAt,
                status = RoomApplicationStatus.ACCEPTED,
                pendingMemberId = null,
            ),
        )
        return resumeSubmissionRepository.saveAndFlush(
            ResumeSubmissionEntity(
                roomApplicationId = application.id,
                roomId = roomId,
                memberId = memberId,
                sourceResumeId = UUID.randomUUID(),
                fileKey = "resumes/$memberId/backend.pdf",
                originalName = "backend.pdf",
                sizeBytes = 1024L,
                contentType = "application/pdf",
                submittedAt = submittedAt,
            ),
        ).id
    }
}
