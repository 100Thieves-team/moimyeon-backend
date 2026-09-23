package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.ReviewSkipEntity
import io.plady.moimyeon.storage.db.core.ReviewSkipRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.UUID

class QaMemberResetterIT(
    private val qaMemberResetter: QaMemberResetter,
    private val jdbcTemplate: JdbcTemplate,
    private val roomRepository: RoomRepository,
    private val memberRepository: MemberRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val reviewRepository: ReviewRepository,
    private val reviewSkipRepository: ReviewSkipRepository,
) : ContextTest() {
    private val testerId = UUID.randomUUID()
    private val otherId = UUID.randomUUID()
    private val hostedQaRoomId = UUID.randomUUID()
    private val hostedLiveRoomId = UUID.randomUUID()
    private val othersQaRoomId = UUID.randomUUID()
    private val othersLiveRoomId = UUID.randomUUID()
    private val at = LocalDateTime.of(2026, 9, 22, 10, 0)
    private val seededRoomIds = listOf(hostedQaRoomId, hostedLiveRoomId, othersQaRoomId, othersLiveRoomId)

    @AfterEach
    fun cleanUp() {
        seededRoomIds.forEach { roomId ->
            listOf(
                "delete from review_tag where review_id in (select id from review where room_id = ?)",
                "delete from review where room_id = ?",
                "delete from review_skip where room_id = ?",
                "delete from participation where room_id = ?",
                "delete from room_application where room_id = ?",
                "delete from room where id = ?",
            ).forEach { jdbcTemplate.update(it, bytes(roomId)) }
        }
        memberRepository.deleteAllById(listOf(testerId, otherId))
    }

    @Test
    fun `방장인 QA 룸과 본인의 참여·신청, QA 룸의 후기를 지우고 회원 행은 남긴다`() {
        seedMembers()
        seedRoom(hostedQaRoomId, "[QA] 내가 만든 룸", hostMemberId = testerId)
        seedRoom(othersQaRoomId, "[QA] 남이 만든 룸", hostMemberId = otherId)
        seedRoom(othersLiveRoomId, "남이 만든 실데이터 룸", hostMemberId = otherId)
        joinAsParticipant(othersQaRoomId, testerId)
        joinAsParticipant(othersLiveRoomId, testerId)
        applyPending(othersLiveRoomId, testerId)
        seedReview(othersQaRoomId, author = otherId, target = testerId)
        seedReview(othersQaRoomId, author = testerId, target = otherId)
        seedReview(othersLiveRoomId, author = otherId, target = testerId)
        reviewSkipRepository.saveAndFlush(ReviewSkipEntity(roomId = othersQaRoomId, authorMemberId = testerId, targetMemberId = otherId))

        val deleted = qaMemberResetter.reset(testerId)

        assertThat(deleted.rooms).isEqualTo(1)
        assertThat(deleted.participants).isEqualTo(3)
        assertThat(deleted.applications).isEqualTo(2)
        assertThat(deleted.reviews).isEqualTo(2)
        assertThat(deleted.reviewTags).isEqualTo(2)
        assertThat(deleted.reviewSkips).isEqualTo(1)

        assertThat(roomRepository.existsById(hostedQaRoomId)).isFalse()
        assertThat(roomRepository.existsById(othersQaRoomId)).isTrue()
        assertThat(roomRepository.existsById(othersLiveRoomId)).isTrue()
        assertThat(participationRepository.findAll().filter { it.memberId == testerId }).isEmpty()
        assertThat(roomApplicationRepository.findAll().filter { it.applicantMemberId == testerId }).isEmpty()
        assertThat(participationRepository.findAll().filter { it.memberId == otherId }).hasSize(2)
        assertThat(reviewRepository.findAll().filter { it.targetMemberId == testerId }.map { it.roomId }).containsExactly(othersLiveRoomId)
        assertThat(memberRepository.existsById(testerId)).isTrue()
    }

    @Test
    fun `방장인 룸 중 QA 마커가 없는 룸이 있으면 E2201 로 거절하고 아무것도 지우지 않는다`() {
        seedMembers()
        seedRoom(hostedQaRoomId, "[QA] 내가 만든 룸", hostMemberId = testerId)
        seedRoom(hostedLiveRoomId, "내가 만든 실데이터 룸", hostMemberId = testerId)
        seedRoom(othersQaRoomId, "[QA] 남이 만든 룸", hostMemberId = otherId)
        joinAsParticipant(othersQaRoomId, testerId)

        assertThatThrownBy { qaMemberResetter.reset(testerId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }

        assertThat(roomRepository.existsById(hostedQaRoomId)).isTrue()
        assertThat(roomRepository.existsById(hostedLiveRoomId)).isTrue()
        assertThat(participationRepository.findAll().filter { it.memberId == testerId }).hasSize(3)
    }

    @Test
    fun `없는 회원은 E1006 을 던진다`() {
        assertThatThrownBy { qaMemberResetter.reset(UUID.randomUUID()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    @Test
    fun `룸이 하나도 없는 회원은 0 건으로 끝난다`() {
        seedMembers()

        assertThat(qaMemberResetter.reset(testerId)).isEqualTo(QaDeletedRows.NONE)
    }

    private fun seedMembers() {
        listOf(testerId to "tester", otherId to "other").forEach { (id, label) ->
            val suffix = id.toString().take(8)
            memberRepository.saveAndFlush(
                MemberEntity(
                    id = id,
                    email = "qa-reset-$label-$suffix@example.com",
                    nickname = "qr$label$suffix",
                    status = MemberStatus.ACTIVE,
                    lastLoginAt = at,
                    socialAccounts = listOf(
                        SocialAccountEntity(
                            provider = SocialLoginProvider.GOOGLE,
                            providerId = "qa-reset-$label-$suffix",
                            linkedEmail = "qa-reset-$label-$suffix@example.com",
                        ),
                    ),
                ),
            )
        }
    }

    private fun seedRoom(roomId: UUID, title: String, hostMemberId: UUID) {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                sigunguId = null,
                title = title,
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = at.plusDays(7),
                durationMinutes = 60,
            ),
        )
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = at,
            ),
        )
        roomApplicationRepository.saveAndFlush(RoomApplicationEntity.forHost(roomId, hostMemberId, at))
    }

    private fun joinAsParticipant(roomId: UUID, memberId: UUID) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = at,
            ),
        )
    }

    private fun applyPending(roomId: UUID, memberId: UUID) {
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = memberId,
                note = "참여하고 싶습니다",
                appliedAt = at,
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = memberId,
            ),
        )
    }

    private fun seedReview(roomId: UUID, author: UUID, target: UUID) {
        reviewRepository.saveAndFlush(
            ReviewEntity(
                roomId = roomId,
                authorMemberId = author,
                targetMemberId = target,
                content = "후기",
                anonymous = false,
                visibleAt = at,
                tags = listOf("KIND"),
            ),
        )
    }

    private fun bytes(uuid: UUID): ByteArray = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
}
