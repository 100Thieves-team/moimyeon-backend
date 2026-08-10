package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomApplicationFinderIT(
    val roomApplicationFinder: RoomApplicationFinder,
    val roomRepository: RoomRepository,
    val roomApplicationRepository: RoomApplicationRepository,
    val participationRepository: ParticipationRepository,
    val memberRepository: MemberRepository,
) : ContextTest() {
    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val createdMemberIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        roomRepository.deleteById(roomId)
        memberRepository.deleteAllById(createdMemberIds)
    }

    @Test
    fun `방장은 철회를 뺀 신청을 신청 시각 순으로 조회하고 닉네임이 채워진다`() {
        // given
        seedRoom()
        seedHost()
        val early = seedApplicant("일찍낸 신청자")
        val late = seedApplicant("늦게낸 신청자")
        val withdrawn = seedApplicant("철회한 신청자")
        seedApplication(late, RoomApplicationStatus.PENDING, appliedAt = now.plusMinutes(10))
        seedApplication(early, RoomApplicationStatus.PENDING, appliedAt = now)
        seedApplication(withdrawn, RoomApplicationStatus.WITHDRAWN, appliedAt = now.plusMinutes(5))

        // when
        val views = roomApplicationFinder.getApplications(roomId, hostId)

        // then — 철회 제외, 신청 시각 오름차순, 닉네임 채움
        assertThat(views.map { it.applicantMemberId }).containsExactly(early, late)
        assertThat(views.map { it.applicantNickname }).containsExactly("일찍낸 신청자", "늦게낸 신청자")
        assertThat(views).noneMatch { it.applicantMemberId == withdrawn }
    }

    // 지우면 몇 명이 기다리고 있었는지가 사라진다(MOI-394).
    @Test
    fun `룸 취소나 확정으로 끝난 신청도 목록에 남는다`() {
        seedRoom()
        seedHost()
        val canceled = seedApplicant("룸 취소로 끝난 신청자")
        val confirmed = seedApplicant("확정으로 끝난 신청자")
        seedApplication(canceled, RoomApplicationStatus.ROOM_CANCELED, appliedAt = now)
        seedApplication(confirmed, RoomApplicationStatus.ROOM_CONFIRMED, appliedAt = now.plusMinutes(5))

        val views = roomApplicationFinder.getApplications(roomId, hostId)

        assertThat(views.map { it.applicantMemberId }).containsExactly(canceled, confirmed)
        assertThat(views.map { it.status })
            .containsExactly(RoomApplicationStatus.ROOM_CANCELED, RoomApplicationStatus.ROOM_CONFIRMED)
    }

    @Test
    fun `방장이 아니면 신청 목록 조회는 ROOM_FORBIDDEN`() {
        seedRoom()
        seedHost()

        assertThatThrownBy { roomApplicationFinder.getApplications(roomId, UUID.randomUUID()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_FORBIDDEN)
            }
    }

    private fun seedRoom() {
        roomRepository.save(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = now.plusDays(3),
                durationMinutes = 60,
            ),
        )
    }

    private fun seedHost() {
        createMember(hostId, "방장 닉네임")
        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
    }

    private fun seedApplicant(nickname: String): UUID {
        val memberId = UUID.randomUUID()
        createMember(memberId, nickname)
        return memberId
    }

    private fun createMember(memberId: UUID, nickname: String) {
        memberRepository.save(
            MemberEntity(
                id = memberId,
                email = "$memberId@test.moimyeon",
                nickname = nickname,
                status = MemberStatus.ACTIVE,
                lastLoginAt = now,
            ),
        )
        createdMemberIds += memberId
    }

    private fun seedApplication(applicantId: UUID, status: RoomApplicationStatus, appliedAt: LocalDateTime) {
        roomApplicationRepository.save(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantId,
                note = "잘 부탁드립니다.",
                appliedAt = appliedAt,
                status = status,
                pendingMemberId = if (status == RoomApplicationStatus.PENDING) applicantId else null,
            ),
        )
    }
}
