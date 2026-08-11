package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.roomapplication.ResumeSubmission
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionManager
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.util.UUID

// 이 이슈가 조용히 틀릴 수 있는 유일한 자리다. 룸 생성이 방장의 신청 행을 만들기 시작하면
// 그 행이 신청 도메인의 판정 네 개(대기 수·개인 대기 한도·대기 유니크·처리 가드)에 섞여 들어갈 수 있고,
// 섞이면 "방장이 다른 룸에 신청을 못 한다" 같은 형태로 한참 뒤에 드러난다.
//
// 각 테스트는 방장 행이 **실제로 생겼다는 것을 먼저 단언한 뒤** 판정이 그대로인지 본다.
// 존재 단언이 없으면 행이 아예 없을 때도 전부 통과해 가드가 비어 버린다.
@Import(FixedClockTestConfiguration::class)
class RoomCreationApplicationRegressionIT(
    private val roomManager: RoomManager,
    private val roomApplicationManager: RoomApplicationManager,
    private val roomApplicationSubmissionManager: RoomApplicationSubmissionManager,
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val participationRepository: ParticipationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val hostMemberId = UUID.randomUUID()
    private val createdRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        createdRoomIds.forEach { roomId ->
            resumeSubmissionRepository.deleteAll(resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(roomId))
            roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
            participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
            roomRepository.deleteById(roomId)
        }
        memberRepository.deleteById(hostMemberId)
    }

    @Test
    fun `룸을 만들어도 방장의 대기 신청 수는 늘지 않는다`() {
        val roomId = createRoom()

        assertThat(hostApplication(roomId).status).isEqualTo(RoomApplicationStatus.ACCEPTED)

        assertThat(
            roomApplicationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(
                roomId,
                RoomApplicationStatus.PENDING,
            ),
        ).isZero()
        assertThat(roomApplicationRepository.countPendingByRoomIds(listOf(roomId))).isEmpty()
    }

    // 개인 대기 한도는 3건이다. 방장 행이 PENDING 이면 룸 세 개를 만든 방장이 신청을 아예 못 하게 된다.
    @Test
    fun `룸을 3개 만든 방장도 다른 룸에 참가 신청할 수 있다`() {
        val hostRoomIds = List(3) { createRoom() }
        hostRoomIds.forEach { assertThat(hostApplication(it).status).isEqualTo(RoomApplicationStatus.ACCEPTED) }

        assertThat(
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                hostMemberId,
                RoomApplicationStatus.PENDING,
            ),
        ).isZero()

        persistHostMember()
        val otherRoomId = createRoom(hostMemberId = UUID.randomUUID())

        assertThatCode {
            roomApplicationSubmissionManager.submit(
                hostMemberId,
                otherRoomId,
                "참여하고 싶습니다.",
                ResumeSubmission(UUID.randomUUID(), resumeFile()),
            )
        }.doesNotThrowAnyException()
    }

    // 대기 유니크는 (room_id, pending_member_id) 다. 방장 행이 pending_member_id 를 차지하면
    // 방장이 자기 룸의 대기 한 자리를 영구히 점유한 상태가 된다.
    @Test
    fun `방장 행은 대기 유니크 자리를 차지하지 않는다`() {
        val roomId = createRoom()

        assertThat(hostApplication(roomId).pendingMemberId).isNull()
        assertThat(
            roomApplicationRepository.existsByRoomIdAndPendingMemberIdAndDeletedAtIsNull(roomId, hostMemberId),
        ).isFalse()
    }

    @Test
    fun `방장이 자기 룸에 참가 신청하면 E1414 를 던진다`() {
        val roomId = createRoom()
        assertThat(hostApplication(roomId).status).isEqualTo(RoomApplicationStatus.ACCEPTED)
        persistHostMember()

        assertThatThrownBy {
            roomApplicationSubmissionManager.submit(
                hostMemberId,
                roomId,
                "제 룸이지만 신청해 봅니다.",
                ResumeSubmission(UUID.randomUUID(), resumeFile()),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_HOST_CANNOT_APPLY)
        }
    }

    // 방장 행은 목록에 그대로 노출된다(D7). 방장이 자기 행에 수락·반려를 눌러도 상태가 바뀌면 안 된다.
    @Test
    fun `방장 행에 수락이나 반려를 시도하면 E1409 를 던진다`() {
        val roomId = createRoom()
        val applicationId = hostApplication(roomId).id

        assertHandlingFails(CoreErrorType.APPLICATION_ALREADY_HANDLED) {
            roomApplicationManager.accept(roomId, applicationId, hostMemberId)
        }
        assertHandlingFails(CoreErrorType.APPLICATION_ALREADY_HANDLED) {
            roomApplicationManager.reject(roomId, applicationId, hostMemberId, null)
        }
        assertHandlingFails(CoreErrorType.APPLICATION_ALREADY_HANDLED) {
            roomApplicationSubmissionManager.withdraw(hostMemberId, roomId)
        }

        assertThat(hostApplication(roomId).status).isEqualTo(RoomApplicationStatus.ACCEPTED)
    }

    private fun assertHandlingFails(errorType: CoreErrorType, handling: () -> Unit) {
        assertThatThrownBy { handling() }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    private fun hostApplication(roomId: UUID): RoomApplicationEntity {
        val applications = roomApplicationRepository.findAll()
            .filter { it.roomId == roomId && it.applicantMemberId == hostMemberId }
        assertThat(applications).hasSize(1)
        return applications.single()
    }

    private fun createRoom(hostMemberId: UUID = this.hostMemberId): UUID {
        val room = Room.create(
            id = UUID.randomUUID(),
            jobPostingId = 1L,
            jobRoleId = 1L,
            title = RoomTitle("백엔드 모의면접 함께 준비해요"),
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingPlace = MeetingPlace.Online,
            capacity = RoomCapacity(min = 2, max = 6),
            schedule = RoomSchedule(startAt = FIXED_NOW.plusDays(7), durationMinutes = 60),
            resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
            now = FIXED_NOW,
        )
        roomManager.create(room, hostMemberId, UUID.randomUUID(), resumeFile())
        createdRoomIds += room.id
        return room.id
    }

    // 신청 경로는 회원이 실재해야 통과한다(MemberValidator). 방장이 신청자로 나서는 테스트에서만 필요하다.
    private fun persistHostMember() {
        memberRepository.save(
            MemberEntity(
                id = hostMemberId,
                email = "host-moi333@example.com",
                nickname = "방장333",
                status = MemberStatus.ACTIVE,
                lastLoginAt = FIXED_NOW,
                socialAccounts = listOf(
                    SocialAccountEntity(
                        provider = SocialLoginProvider.GOOGLE,
                        providerId = "host-moi333",
                        linkedEmail = "host-moi333@example.com",
                    ),
                ),
            ),
        )
    }

    private fun resumeFile() = ResumeFile(
        key = "resumes/$hostMemberId/backend.pdf",
        originalName = "backend.pdf",
        sizeBytes = 1024L,
        contentType = "application/pdf",
    )
}
