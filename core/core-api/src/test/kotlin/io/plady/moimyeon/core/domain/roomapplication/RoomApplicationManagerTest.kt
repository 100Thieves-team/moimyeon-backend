package io.plady.moimyeon.core.domain.roomapplication

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.member.MemberValidator
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.room.RoomValidator
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class RoomApplicationManagerTest {
    private val memberValidator = mockk<MemberValidator>()
    private val roomValidator = mockk<RoomValidator>()
    private val participationValidator = mockk<ParticipationValidator>()
    private val roomApplicationRepository = mockk<RoomApplicationRepository>()
    private val resumeSubmissionRepository = mockk<ResumeSubmissionRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC)
    private val manager = RoomApplicationManager(
        memberValidator,
        roomValidator,
        participationValidator,
        roomApplicationRepository,
        resumeSubmissionRepository,
        clock,
    )

    private val now = LocalDateTime.of(2026, 8, 4, 12, 0)
    private val applicantMemberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val applicationForm = RoomApplicationForm(resumeId, "실전처럼 연습하고 싶어요.")
    private val resumeSubmission = ResumeSubmission(
        sourceResumeId = resumeId,
        file = ResumeFile(
            key = "resumes/$applicantMemberId/source.pdf",
            originalName = "backend.pdf",
            sizeBytes = 1024L,
            contentType = "application/pdf",
        ),
    )

    @BeforeEach
    fun setUp() {
        givenEligibleApplication()
    }

    @Test
    fun `신청서를 제출하면 각 격벽의 판정을 거쳐 대기 신청과 이력서 참조를 저장한다`() {
        val savedApplication = mockk<RoomApplicationEntity> {
            every { id } returns 1L
        }
        val applicationSlot = slot<RoomApplicationEntity>()
        val submissionSlot = slot<ResumeSubmissionEntity>()
        every { roomApplicationRepository.saveAndFlush(capture(applicationSlot)) } returns savedApplication
        every { resumeSubmissionRepository.save(capture(submissionSlot)) } answers { firstArg() }

        val applicationId = manager.submit(applicantMemberId, roomId, applicationForm.note, resumeSubmission)

        assertThat(applicationId).isEqualTo(1L)
        assertThat(applicationSlot.captured.roomId).isEqualTo(roomId)
        assertThat(applicationSlot.captured.applicantMemberId).isEqualTo(applicantMemberId)
        assertThat(applicationSlot.captured.note).isEqualTo(applicationForm.note)
        assertThat(applicationSlot.captured.status).isEqualTo(RoomApplicationStatus.PENDING)
        assertThat(applicationSlot.captured.appliedAt).isEqualTo(now)
        assertThat(submissionSlot.captured.roomApplicationId).isEqualTo(savedApplication.id)
        assertThat(submissionSlot.captured.sourceResumeId).isEqualTo(resumeId)
        assertThat(submissionSlot.captured.fileKey).isEqualTo(resumeSubmission.file.key)
        assertThat(submissionSlot.captured.originalName).isEqualTo(resumeSubmission.file.originalName)
        assertThat(submissionSlot.captured.sizeBytes).isEqualTo(resumeSubmission.file.sizeBytes)
        assertThat(submissionSlot.captured.contentType).isEqualTo(resumeSubmission.file.contentType)
        assertThat(submissionSlot.captured.roomId).isEqualTo(roomId)
        assertThat(submissionSlot.captured.memberId).isEqualTo(applicantMemberId)
        assertThat(submissionSlot.captured.submittedAt).isEqualTo(now)
        verifyOrder {
            memberValidator.validateActive(applicantMemberId)
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            )
            roomValidator.validateAcceptingApplications(roomId)
            participationValidator.validateNotHost(roomId, applicantMemberId)
            participationValidator.validateNotParticipating(roomId, applicantMemberId)
            roomApplicationRepository.existsByRoomIdAndPendingMemberIdAndDeletedAtIsNull(
                roomId,
                applicantMemberId,
            )
            roomApplicationRepository.existsByRoomIdAndApplicantMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                applicantMemberId,
                RoomApplicationStatus.REJECTED,
            )
            participationValidator.validateNoRemovalHistory(roomId, applicantMemberId)
            roomApplicationRepository.saveAndFlush(any())
            resumeSubmissionRepository.save(any())
        }
    }

    @Test
    fun `전달 사항이 없으면 빈 문자열로 신청을 저장한다`() {
        val savedApplication = mockk<RoomApplicationEntity> {
            every { id } returns 1L
        }
        val applicationSlot = slot<RoomApplicationEntity>()
        every { roomApplicationRepository.saveAndFlush(capture(applicationSlot)) } returns savedApplication
        every { resumeSubmissionRepository.save(any()) } answers { firstArg() }

        manager.submit(applicantMemberId, roomId, "", resumeSubmission)

        assertThat(applicationSlot.captured.note).isEmpty()
    }

    @Test
    fun `활성 회원이 아니면 신청 가능 건수를 확인하지 않는다`() {
        every {
            memberValidator.validateActive(applicantMemberId)
        } throws CoreException(CoreErrorType.MEMBER_NOT_ACTIVE)

        assertSubmissionFails(CoreErrorType.MEMBER_NOT_ACTIVE)

        verify(exactly = 0) {
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(any(), any())
        }
        verify(exactly = 0) { roomValidator.validateAcceptingApplications(any()) }
    }

    @Test
    fun `동시에 대기 중인 참가 신청이 2건이면 세 번째 신청을 접수한다`() {
        every {
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            )
        } returns 2L
        val savedApplication = mockk<RoomApplicationEntity> {
            every { id } returns 1L
        }
        every { roomApplicationRepository.saveAndFlush(any()) } returns savedApplication
        every { resumeSubmissionRepository.save(any()) } answers { firstArg() }

        val applicationId = manager.submit(applicantMemberId, roomId, applicationForm.note, resumeSubmission)

        assertThat(applicationId).isEqualTo(1L)
    }

    @Test
    fun `동시에 대기 중인 참가 신청이 3건이면 추가 신청을 거부한다`() {
        every {
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            )
        } returns 3L

        assertSubmissionFails(CoreErrorType.ROOM_APPLICATION_PENDING_LIMIT_EXCEEDED)

        verify(exactly = 0) { roomValidator.validateAcceptingApplications(any()) }
    }

    @Test
    fun `룸이 신청을 받을 수 없으면 이후 신청자 조건을 확인하지 않는다`() {
        every {
            roomValidator.validateAcceptingApplications(roomId)
        } throws CoreException(CoreErrorType.ROOM_NOT_RECRUITING)

        assertSubmissionFails(CoreErrorType.ROOM_NOT_RECRUITING)

        verify(exactly = 0) { participationValidator.validateNotHost(any(), any()) }
    }

    @Test
    fun `이미 대기 중인 신청이 있으면 ROOM_APPLICATION_DUPLICATED 로 신청을 거부한다`() {
        every {
            roomApplicationRepository.existsByRoomIdAndPendingMemberIdAndDeletedAtIsNull(roomId, applicantMemberId)
        } returns true

        assertSubmissionFails(CoreErrorType.ROOM_APPLICATION_DUPLICATED)
    }

    @Test
    fun `반려 이력이 있으면 ROOM_REAPPLICATION_NOT_ALLOWED 로 재신청을 거부한다`() {
        every {
            roomApplicationRepository.existsByRoomIdAndApplicantMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                applicantMemberId,
                RoomApplicationStatus.REJECTED,
            )
        } returns true

        assertSubmissionFails(CoreErrorType.ROOM_REAPPLICATION_NOT_ALLOWED)
    }

    @Test
    fun `동시 요청의 대기 신청 유니크 충돌은 ROOM_APPLICATION_DUPLICATED 로 변환한다`() {
        every {
            roomApplicationRepository.saveAndFlush(any())
        } throws DataIntegrityViolationException(
            "duplicate pending application",
            SQLException("uk_room_application_room_pending_active"),
        )

        assertSubmissionFails(CoreErrorType.ROOM_APPLICATION_DUPLICATED)
    }

    @Test
    fun `대기 신청 유니크 충돌이 아닌 무결성 위반은 중복 신청으로 오인하지 않고 전파한다`() {
        val unexpected = DataIntegrityViolationException(
            "resume submission member_id cannot be null",
            SQLException("not-null constraint"),
        )
        every { roomApplicationRepository.saveAndFlush(any()) } throws unexpected

        assertThatThrownBy {
            manager.submit(applicantMemberId, roomId, applicationForm.note, resumeSubmission)
        }.isSameAs(unexpected)
        verify(exactly = 0) { resumeSubmissionRepository.save(any()) }
    }

    @Test
    fun `본인의 대기 신청을 잠근 뒤 철회하고 신청과 제출 기록은 보존한다`() {
        val application = application(RoomApplicationStatus.PENDING)
        every {
            roomApplicationRepository
                .findFirstForUpdateByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
        } returns application

        manager.withdraw(applicantMemberId, roomId)

        assertThat(application.status).isEqualTo(RoomApplicationStatus.WITHDRAWN)
        assertThat(application.pendingMemberId).isNull()
        assertThat(application.handledAt).isEqualTo(now)
        assertThat(application.handlerMemberId).isNull()
        assertThat(application.isActive()).isTrue()
        verify(exactly = 1) {
            roomApplicationRepository
                .findFirstForUpdateByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
        }
        verify(exactly = 0) { roomApplicationRepository.delete(any()) }
        verify(exactly = 0) { resumeSubmissionRepository.delete(any()) }
    }

    @Test
    fun `해당 룸에 본인의 신청이 없으면 APPLICATION_NOT_FOUND를 던진다`() {
        every {
            roomApplicationRepository
                .findFirstForUpdateByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                )
        } returns null

        assertWithdrawalFails(CoreErrorType.APPLICATION_NOT_FOUND)
    }

    @Test
    fun `최신 신청이 이미 처리되었으면 APPLICATION_ALREADY_HANDLED를 던진다`() {
        listOf(
            RoomApplicationStatus.ACCEPTED,
            RoomApplicationStatus.REJECTED,
            RoomApplicationStatus.WITHDRAWN,
        ).forEach { status ->
            every {
                roomApplicationRepository
                    .findFirstForUpdateByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                        roomId,
                        applicantMemberId,
                    )
            } returns application(status)

            assertWithdrawalFails(CoreErrorType.APPLICATION_ALREADY_HANDLED)
        }
    }

    private fun givenEligibleApplication() {
        justRun { memberValidator.validateActive(applicantMemberId) }
        every {
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            )
        } returns 0L
        justRun { roomValidator.validateAcceptingApplications(roomId) }
        justRun { participationValidator.validateNotHost(roomId, applicantMemberId) }
        justRun { participationValidator.validateNotParticipating(roomId, applicantMemberId) }
        justRun { participationValidator.validateNoRemovalHistory(roomId, applicantMemberId) }
        every {
            roomApplicationRepository.existsByRoomIdAndApplicantMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                applicantMemberId,
                RoomApplicationStatus.REJECTED,
            )
        } returns false
        every {
            roomApplicationRepository.existsByRoomIdAndPendingMemberIdAndDeletedAtIsNull(roomId, applicantMemberId)
        } returns false
    }

    private fun application(status: RoomApplicationStatus): RoomApplicationEntity {
        return RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = applicantMemberId,
            note = applicationForm.note,
            appliedAt = now.minusHours(1),
            status = status,
            pendingMemberId = applicantMemberId.takeIf { status == RoomApplicationStatus.PENDING },
        )
    }

    private fun assertSubmissionFails(errorType: CoreErrorType) {
        assertThatThrownBy { manager.submit(applicantMemberId, roomId, applicationForm.note, resumeSubmission) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
        verify(exactly = 0) { resumeSubmissionRepository.save(any()) }
    }

    private fun assertWithdrawalFails(errorType: CoreErrorType) {
        assertThatThrownBy { manager.withdraw(applicantMemberId, roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
