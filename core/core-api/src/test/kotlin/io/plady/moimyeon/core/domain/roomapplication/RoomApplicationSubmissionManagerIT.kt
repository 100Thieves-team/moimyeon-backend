package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomApplicationSubmissionManagerIT(
    private val roomApplicationSubmissionManager: RoomApplicationSubmissionManager,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
) : ContextTest() {
    private val applicantMemberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val otherRoomId = UUID.randomUUID()
    private val otherMemberId = UUID.randomUUID()

    @AfterEach
    fun cleanUp() {
        val roomIds = setOf(roomId, otherRoomId)
        resumeSubmissionRepository.deleteAll(resumeSubmissionRepository.findAll().filter { it.roomId in roomIds })
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId in roomIds })
    }

    @Test
    fun `철회 상태를 커밋하고 신청과 제출 이력은 보존한다`() {
        val application = roomApplicationRepository.save(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantMemberId,
                note = "실전처럼 준비하고 싶어요.",
                appliedAt = LocalDateTime.of(2026, 8, 5, 12, 0),
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = applicantMemberId,
            ),
        )
        resumeSubmissionRepository.save(
            ResumeSubmissionEntity(
                roomApplicationId = application.id,
                roomId = roomId,
                memberId = applicantMemberId,
                sourceResumeId = UUID.randomUUID(),
                fileKey = "resumes/$applicantMemberId/source.pdf",
                originalName = "backend.pdf",
                sizeBytes = 1024L,
                contentType = "application/pdf",
                submittedAt = LocalDateTime.of(2026, 8, 5, 12, 0),
            ),
        )

        roomApplicationSubmissionManager.withdraw(applicantMemberId, roomId)

        val withdrawn = roomApplicationRepository.findById(application.id).orElseThrow()
        assertThat(withdrawn.status).isEqualTo(RoomApplicationStatus.WITHDRAWN)
        assertThat(withdrawn.pendingMemberId).isNull()
        assertThat(withdrawn.handledAt).isNotNull()
        assertThat(withdrawn.handlerMemberId).isNull()
        assertThat(withdrawn.isActive()).isTrue()
        assertThat(
            resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(application.id),
        ).isNotNull()
    }

    // 판정이 보는 것이 REJECTED 하나라는 사실을 고정한다 — "끝난 신청 전부"로 넓히면 깨진다.
    @Test
    fun `룸 취소나 확정으로 끝난 신청은 재신청 차단 판정에 걸리지 않는다`() {
        persistApplication(RoomApplicationStatus.ROOM_CANCELED)
        persistApplication(RoomApplicationStatus.ROOM_CONFIRMED)

        val blocked = roomApplicationRepository.existsByRoomIdAndApplicantMemberIdAndStatusAndDeletedAtIsNull(
            roomId,
            applicantMemberId,
            RoomApplicationStatus.REJECTED,
        )

        assertThat(blocked).isFalse()
    }

    @Test
    fun `탈퇴한 회원의 대기 신청은 모든 룸에서 철회되고 끝난 신청과 다른 회원의 신청은 그대로다`() {
        val pending = persistApplication(RoomApplicationStatus.PENDING)
        val otherRoomPending = persistApplication(RoomApplicationStatus.PENDING, roomId = otherRoomId)
        val rejected = persistApplication(RoomApplicationStatus.REJECTED, roomId = otherRoomId)
        val otherMemberPending = persistApplication(RoomApplicationStatus.PENDING, applicant = otherMemberId)
        val now = LocalDateTime.of(2026, 9, 24, 12, 0)

        roomApplicationSubmissionManager.withdrawAllPending(applicantMemberId, now)

        listOf(pending, otherRoomPending).map { roomApplicationRepository.findById(it).orElseThrow() }.forEach {
            assertThat(it.status).isEqualTo(RoomApplicationStatus.WITHDRAWN)
            assertThat(it.pendingMemberId).isNull()
            assertThat(it.handledAt).isEqualTo(now)
        }
        assertThat(roomApplicationRepository.findById(rejected).orElseThrow().status).isEqualTo(RoomApplicationStatus.REJECTED)
        assertThat(roomApplicationRepository.findById(otherMemberPending).orElseThrow().status)
            .isEqualTo(RoomApplicationStatus.PENDING)
    }

    private fun persistApplication(
        status: RoomApplicationStatus,
        roomId: UUID = this.roomId,
        applicant: UUID = applicantMemberId,
    ): Long = roomApplicationRepository.save(
        RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = applicant,
            note = "",
            appliedAt = LocalDateTime.of(2026, 8, 5, 12, 0),
            status = status,
            pendingMemberId = if (status == RoomApplicationStatus.PENDING) applicant else null,
        ),
    ).id
}
