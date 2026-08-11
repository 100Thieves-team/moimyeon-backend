package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// canViewOriginal 은 세 조건의 AND 라 하나만 빠뜨려도 조용히 열린다. 원본 발급(MOI-414)이 이 플래그를
// 그대로 믿을 예정이라 여기가 마지막 방어선이다.
@Transactional
class RoomParticipantReaderIT(
    private val roomParticipantReader: RoomParticipantReader,
    private val roomRepository: RoomRepository,
    private val memberRepository: MemberRepository,
    private val participationRepository: ParticipationRepository,
    private val resumeRepository: ResumeRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()

    private val confirmedAt = LocalDateTime.of(2026, 8, 5, 12, 0)
    private val beforeConfirmation = confirmedAt.minusDays(2)
    private val afterConfirmation = confirmedAt.plusHours(1)
    private val startAt = LocalDateTime.of(2026, 9, 1, 19, 0)

    // --- canViewOriginal (Step 3) -------------------------------------------------

    @Test
    fun `원본 비공개 룸에서는 확정 참여자에게도 원본을 열 수 없다`() {
        givenConfirmedRoom(resumePublic = false)

        val participants = roomParticipantReader.getAllByRoom(roomId, participantMemberId)

        assertThat(participants).isNotEmpty().allMatch { !it.canViewOriginal }
    }

    @Test
    fun `확정 전에는 원본 공개 룸의 방장에게도 원본을 열 수 없다`() {
        persistRoom(resumePublic = true, status = RoomStatus.RECRUITING)
        joinWithResume(hostMemberId, ParticipationRole.HOST, "든든한곰")
        joinWithResume(participantMemberId, ParticipationRole.PARTICIPANT, "라이언")

        val participants = roomParticipantReader.getAllByRoom(roomId, hostMemberId)

        assertThat(participants).isNotEmpty().allMatch { !it.canViewOriginal }
    }

    @Test
    fun `원본 공개 확정 룸의 확정 참여자는 원본을 열 수 있다`() {
        givenConfirmedRoom(resumePublic = true)

        val participants = roomParticipantReader.getAllByRoom(roomId, participantMemberId)

        assertThat(participants).isNotEmpty().allMatch { it.canViewOriginal }
    }

    @Test
    fun `확정 후에 합류한 참여자는 원본을 열 수 없다`() {
        givenConfirmedRoom(resumePublic = true)
        val lateMemberId = UUID.randomUUID()
        joinWithResume(lateMemberId, ParticipationRole.PARTICIPANT, "어피치", joinedAt = afterConfirmation)

        val participants = roomParticipantReader.getAllByRoom(roomId, lateMemberId)

        assertThat(participants).isNotEmpty().allMatch { !it.canViewOriginal }
    }

    // --- 명부 내용과 정렬 (Step 4) --------------------------------------------------

    @Test
    fun `명부는 방장을 맨 위에 두고 참여 순서로 정렬한다`() {
        persistRoom(resumePublic = false, status = RoomStatus.RECRUITING)
        val earlyMemberId = UUID.randomUUID()
        val lateMemberId = UUID.randomUUID()
        joinWithResume(earlyMemberId, ParticipationRole.PARTICIPANT, "라이언", joinedAt = beforeConfirmation)
        joinWithResume(lateMemberId, ParticipationRole.PARTICIPANT, "어피치", joinedAt = beforeConfirmation.plusHours(1))
        // 방장이 가장 늦게 들어와도 맨 앞이어야 한다.
        joinWithResume(hostMemberId, ParticipationRole.HOST, "든든한곰", joinedAt = beforeConfirmation.plusHours(2))

        val participants = roomParticipantReader.getAllByRoom(roomId, hostMemberId)

        assertThat(participants.map { it.memberId })
            .containsExactly(hostMemberId, earlyMemberId, lateMemberId)
        assertThat(participants.first().isHost).isTrue()
    }

    @Test
    fun `나간 참여자는 명부에 없다`() {
        persistRoom(resumePublic = false, status = RoomStatus.RECRUITING)
        joinWithResume(hostMemberId, ParticipationRole.HOST, "든든한곰")
        val leftMemberId = UUID.randomUUID()
        persistMember(leftMemberId, "떠난사람")
        persistParticipation(
            memberId = leftMemberId,
            role = ParticipationRole.PARTICIPANT,
            status = ParticipationStatus.LEFT,
            joinedAt = beforeConfirmation,
        )

        val participants = roomParticipantReader.getAllByRoom(roomId, hostMemberId)

        assertThat(participants.map { it.memberId }).containsExactly(hostMemberId)
    }

    // §4.5 의 핵심 - AI 요약은 원본 공개 여부와 무관하게 같은 룸 참여자에게 공개된다.
    @Test
    fun `원본 비공개 룸에서도 AI 요약은 같은 룸 참여자에게 보인다`() {
        persistRoom(resumePublic = false, status = RoomStatus.RECRUITING)
        joinWithResume(hostMemberId, ParticipationRole.HOST, "든든한곰")
        joinWithResume(participantMemberId, ParticipationRole.PARTICIPANT, "라이언", summary = "핀테크 백엔드 3년 차")

        val participants = roomParticipantReader.getAllByRoom(roomId, hostMemberId)
        val participant = participants.single { it.memberId == participantMemberId }

        assertThat(participant.resumeSummary?.content).isEqualTo("핀테크 백엔드 3년 차")
        assertThat(participant.resumeSubmissionId).isNotNull()
        assertThat(participant.canViewOriginal).isFalse()
    }

    @Test
    fun `탈퇴한 회원이 있어도 명부 조회가 실패하지 않는다`() {
        persistRoom(resumePublic = false, status = RoomStatus.RECRUITING)
        joinWithResume(hostMemberId, ParticipationRole.HOST, "든든한곰")
        // 회원 행 없이 참여만 남은 상태 = 탈퇴로 사라진 회원.
        persistParticipation(participantMemberId, ParticipationRole.PARTICIPANT, joinedAt = beforeConfirmation)

        val participants = roomParticipantReader.getAllByRoom(roomId, hostMemberId)

        assertThat(participants.single { it.memberId == participantMemberId }.nickname).isNull()
    }

    // --- 픽스처 -------------------------------------------------------------------

    private fun givenConfirmedRoom(resumePublic: Boolean) {
        persistRoom(resumePublic = resumePublic, status = RoomStatus.CONFIRMED)
        joinWithResume(hostMemberId, ParticipationRole.HOST, "든든한곰")
        joinWithResume(participantMemberId, ParticipationRole.PARTICIPANT, "라이언")
        recordConfirmation()
    }

    private fun joinWithResume(
        memberId: UUID,
        role: ParticipationRole,
        nickname: String,
        joinedAt: LocalDateTime = beforeConfirmation,
        summary: String = "$nickname 의 요약",
    ) {
        persistMember(memberId, nickname)
        persistParticipation(memberId, role, joinedAt = joinedAt)
        persistSubmission(memberId, summary)
    }

    private fun persistMember(memberId: UUID, nickname: String) {
        memberRepository.saveAndFlush(
            MemberEntity(
                id = memberId,
                email = "member-${memberId.toString().take(8)}@example.com",
                nickname = nickname,
                status = MemberStatus.ACTIVE,
                lastLoginAt = beforeConfirmation,
                socialAccounts = listOf(
                    SocialAccountEntity(
                        provider = SocialLoginProvider.GOOGLE,
                        providerId = memberId.toString(),
                        linkedEmail = null,
                    ),
                ),
            ),
        )
    }

    private fun persistParticipation(
        memberId: UUID,
        role: ParticipationRole,
        status: ParticipationStatus = ParticipationStatus.JOINED,
        joinedAt: LocalDateTime = beforeConfirmation,
    ) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = role,
                status = status,
                joinedAt = joinedAt,
                leftAt = joinedAt.plusHours(1).takeIf { status == ParticipationStatus.LEFT },
                leftByMemberId = memberId.takeIf { status == ParticipationStatus.LEFT },
            ),
        )
    }

    private fun persistSubmission(memberId: UUID, summary: String) {
        val resumeId = UUID.randomUUID()
        resumeRepository.saveAndFlush(
            ResumeEntity(
                id = resumeId,
                memberId = memberId,
                name = "이력서.pdf",
                fileKey = "resumes/$memberId/$resumeId.pdf",
                originalName = "이력서.pdf",
                sizeBytes = 1024L,
                contentType = "application/pdf",
                summaryStatus = ResumeSummaryStatus.DONE,
                summaryContent = summary,
                summaryStartedAt = beforeConfirmation,
                isDefault = false,
            ),
        )
        val application = roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = memberId,
                note = "",
                appliedAt = beforeConfirmation,
                status = RoomApplicationStatus.ACCEPTED,
                pendingMemberId = null,
            ),
        )
        resumeSubmissionRepository.saveAndFlush(
            ResumeSubmissionEntity(
                roomApplicationId = application.id,
                roomId = roomId,
                memberId = memberId,
                sourceResumeId = resumeId,
                fileKey = "resumes/$memberId/$resumeId.pdf",
                originalName = "이력서.pdf",
                sizeBytes = 1024L,
                contentType = "application/pdf",
                submittedAt = beforeConfirmation,
            ),
        )
    }

    private fun persistRoom(resumePublic: Boolean, status: RoomStatus) {
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
    }

    // 확정 전이를 만드는 코드가 아직 없다(MOI-398). 로그를 직접 심는다.
    private fun recordConfirmation() {
        entityManager.createNativeQuery(
            """
            insert into room_status_log (
                room_id, transition_type, handler_member_id, occurred_at,
                created_at, updated_at, deleted_at
            ) values (
                :roomId, 'CONFIRMED', :handlerMemberId, :occurredAt,
                :occurredAt, :occurredAt, null
            )
            """.trimIndent(),
        )
            .setParameter("roomId", roomId)
            .setParameter("handlerMemberId", hostMemberId)
            .setParameter("occurredAt", confirmedAt)
            .executeUpdate()
        entityManager.clear()
    }
}
