package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 방장 판정이 역할만 보고 상태를 보지 않던 결함의 회귀 방어.
//
// 나가기(MOI-397)가 들어오면 LEFT 상태의 HOST 행이 생긴다. 그 행을 걸러내지 않으면
// 나간 전 방장이 룸을 수정·취소·확정하고 신청을 수락할 수 있다.
// 지금은 나가기가 없어 도달 불가라 **이 테스트가 유일한 증거**다 — LEFT + HOST 행을 직접 심는다.
class HostDetectionIT(
    private val roomManager: RoomManager,
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val formerHostId = UUID.randomUUID()
    private val currentHostId = UUID.randomUUID()
    private val startAt: LocalDateTime = LocalDateTime.now().plusDays(7)

    @AfterEach
    fun cleanUp() {
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `나간 전 방장은 방장으로 판정되지 않는다`() {
        seedRoomWithFormerHost()

        assertThatThrownBy { roomManager.cancel(roomId, formerHostId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_FORBIDDEN)
            }
    }

    @Test
    fun `룸 상세의 방장은 현재 참여 중인 방장이다`() {
        seedRoomWithFormerHost()

        assertThat(roomFinder.getDetail(roomId).hostMemberId).isEqualTo(currentHostId)
        assertThat(participationFinder.getHostMemberId(roomId)).isEqualTo(currentHostId)
    }

    // 전 방장의 role 은 HOST 로 남긴다(MOI-397 결정) — 최초 방장을 되물을 수 있어야 하므로.
    // 그래서 한 룸에 HOST 행이 둘이 되고, 판정은 status 로 갈라야 한다.
    private fun seedRoomWithFormerHost() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "방장 판정 회귀 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = startAt,
                durationMinutes = 60,
            ),
        )
        participationRepository.saveAllAndFlush(
            listOf(
                ParticipationEntity(
                    roomId = roomId,
                    memberId = formerHostId,
                    participationRole = ParticipationRole.HOST,
                    status = ParticipationStatus.LEFT,
                    joinedAt = startAt.minusDays(5),
                    leftByMemberId = formerHostId,
                    leftAt = startAt.minusDays(4),
                ),
                ParticipationEntity(
                    roomId = roomId,
                    memberId = currentHostId,
                    participationRole = ParticipationRole.HOST,
                    status = ParticipationStatus.JOINED,
                    joinedAt = startAt.minusDays(3),
                ),
            ),
        )
    }
}
