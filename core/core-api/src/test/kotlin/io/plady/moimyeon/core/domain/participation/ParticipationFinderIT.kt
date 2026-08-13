package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
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
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// 참여 슬롯 판정(MOI-427). 쿼리와 룸 상태 집합이 함께 맞아야 값이 맞으므로 Finder 에서 본다.
// Repository 단독 IT 로는 "어느 상태를 세는가"가 검증되지 않는다 (상태 집합은 도메인이 넘긴다).
@Transactional
class ParticipationFinderIT(
    private val participationFinder: ParticipationFinder,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val roomRepository: RoomRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 13, 12, 0)
    private val memberId: UUID = UUID.randomUUID()

    // 룸 상태가 늘면 이 테스트가 먼저 깨진다 — 새 상태가 슬롯을 먹는지 정하지 않고 지나갈 수 없다.
    @Test
    fun `룸 상태별로 참여 슬롯에 세는지 가른다`() {
        val occupies = mapOf(
            RoomStatus.RECRUITING to true,
            RoomStatus.CONFIRMED to true,
            RoomStatus.IN_PROGRESS to true,
            RoomStatus.COMPLETED to false,
            RoomStatus.CANCELED to false,
        )
        assertThat(RoomStatus.entries).containsExactlyInAnyOrderElementsOf(occupies.keys)

        occupies.forEach { (roomStatus, occupied) ->
            val member = UUID.randomUUID()
            join(persistRoom(roomStatus), member)

            assertThat(participationFinder.countOccupiedSlots(member))
                .describedAs("%s 룸의 참여", roomStatus)
                .isEqualTo(if (occupied) 1L else 0L)
        }
    }

    @Test
    fun `나간 참여는 슬롯에 세지 않는다`() {
        join(persistRoom(RoomStatus.RECRUITING), status = ParticipationStatus.LEFT)

        assertThat(participationFinder.countOccupiedSlots(memberId)).isZero()
    }

    // PRD 「룸 참여」 §4.1 은 방장을 슬롯에 포함한다. 룸을 만들면 방장 참여 행이 함께 생긴다.
    @Test
    fun `방장으로 참여한 룸도 슬롯에 센다`() {
        join(persistRoom(RoomStatus.RECRUITING), role = ParticipationRole.HOST)

        assertThat(participationFinder.countOccupiedSlots(memberId)).isEqualTo(1)
    }

    // 참여 슬롯 3개와 대기 신청 3건은 다른 축이다(§4.1, 2026-08-04 확정). 섞이면 최대 참여가 5개가 된다.
    @Test
    fun `처리 대기 중인 신청은 슬롯에 세지 않는다`() {
        join(persistRoom(RoomStatus.RECRUITING))
        applyPending(persistRoom(RoomStatus.RECRUITING))

        assertThat(participationFinder.countOccupiedSlots(memberId)).isEqualTo(1)
    }

    @Test
    fun `참여 중인 룸이 둘이면 슬롯이 남고 셋이면 남지 않는다`() {
        repeat(2) { join(persistRoom(RoomStatus.RECRUITING)) }
        assertThat(participationFinder.hasAvailableSlot(memberId)).isTrue()

        join(persistRoom(RoomStatus.RECRUITING))
        assertThat(participationFinder.hasAvailableSlot(memberId)).isFalse()
    }

    private fun join(
        roomId: UUID,
        joiningMemberId: UUID = memberId,
        status: ParticipationStatus = ParticipationStatus.JOINED,
        role: ParticipationRole = ParticipationRole.PARTICIPANT,
    ): ParticipationEntity = participationRepository.saveAndFlush(
        ParticipationEntity(
            roomId = roomId,
            memberId = joiningMemberId,
            participationRole = role,
            status = status,
            joinedAt = now,
            leftAt = if (status == ParticipationStatus.LEFT) now else null,
        ),
    )

    private fun applyPending(roomId: UUID): RoomApplicationEntity = roomApplicationRepository.saveAndFlush(
        RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = memberId,
            note = "",
            appliedAt = now,
            status = RoomApplicationStatus.PENDING,
            pendingMemberId = memberId,
        ),
    )

    // COMPLETED 로 가는 전이가 아직 없어(MOI-431) 엔티티 메서드로는 만들 수 없다.
    private fun persistRoom(status: RoomStatus): UUID {
        val roomId = roomRepository.saveAndFlush(
            RoomEntity(
                id = UUID.randomUUID(),
                jobPostingId = 1L,
                jobRoleId = 1L,
                sigunguId = null,
                title = "참여 슬롯 판정용 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = null,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = now.plusDays(1),
                durationMinutes = 60,
            ),
        ).id
        entityManager.createNativeQuery("update room set status = :status where id = :roomId")
            .setParameter("status", status.name)
            .setParameter("roomId", roomId)
            .executeUpdate()
        entityManager.clear()
        return roomId
    }
}
