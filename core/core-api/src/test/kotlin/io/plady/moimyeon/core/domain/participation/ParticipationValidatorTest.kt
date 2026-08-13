package io.plady.moimyeon.core.domain.participation

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class ParticipationValidatorTest {
    private val participationRepository = mockk<ParticipationRepository>()
    private val validator = ParticipationValidator(participationRepository)

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every {
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationRole.HOST,
                ParticipationStatus.JOINED,
            )
        } returns false
        every {
            participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            )
        } returns false
        every { participationRepository.existsRemovalHistory(roomId, memberId) } returns false
    }

    @Test
    fun `방장도 참여자도 아니고 내보내진 이력도 없으면 신청자 조건을 충족한다`() {
        assertThatCode {
            validator.validateNotHost(roomId, memberId)
            validator.validateNotParticipating(roomId, memberId)
            validator.validateNoRemovalHistory(roomId, memberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `방장이면 ROOM_HOST_CANNOT_APPLY 로 거부한다`() {
        every {
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationRole.HOST,
                ParticipationStatus.JOINED,
            )
        } returns true

        assertValidationFails(CoreErrorType.ROOM_HOST_CANNOT_APPLY) {
            validator.validateNotHost(roomId, memberId)
        }
    }

    @Test
    fun `현재 방장이면 방장 권한을 확인한다`() {
        every {
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationRole.HOST,
                ParticipationStatus.JOINED,
            )
        } returns true

        assertThatCode { validator.validateHost(roomId, memberId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `현재 방장이 아니면 ROOM_FORBIDDEN 으로 거부한다`() {
        assertValidationFails(CoreErrorType.ROOM_FORBIDDEN) {
            validator.validateHost(roomId, memberId)
        }
    }

    @Test
    fun `이미 참여 중이면 ROOM_APPLICATION_DUPLICATED 로 거부한다`() {
        every {
            participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            )
        } returns true

        assertValidationFails(CoreErrorType.ROOM_APPLICATION_DUPLICATED) {
            validator.validateNotParticipating(roomId, memberId)
        }
    }

    @Test
    fun `내보내진 이력이 있으면 ROOM_REAPPLICATION_NOT_ALLOWED 로 거부한다`() {
        every { participationRepository.existsRemovalHistory(roomId, memberId) } returns true

        assertValidationFails(CoreErrorType.ROOM_REAPPLICATION_NOT_ALLOWED) {
            validator.validateNoRemovalHistory(roomId, memberId)
        }
    }

    // 경계는 "3개 미만"이다(「룸 참여」 §4.1). 셋을 허용하면 참여 중인 룸이 넷이 된다.
    @Test
    fun `참여 중인 룸이 둘이면 신청 슬롯이 남은 것으로 본다`() {
        givenOccupiedSlots(2)

        assertThatCode { validator.validateSlotAvailable(memberId) }.doesNotThrowAnyException()
    }

    @Test
    fun `참여 중인 룸이 셋이면 PARTICIPATION_SLOT_EXCEEDED 로 거부한다`() {
        givenOccupiedSlots(3)

        assertValidationFails(CoreErrorType.PARTICIPATION_SLOT_EXCEEDED) {
            validator.validateSlotAvailable(memberId)
        }
    }

    private fun givenOccupiedSlots(count: Long) {
        every {
            participationRepository.countOccupiedSlotsByMemberId(
                memberId,
                ParticipationSlot.OCCUPYING_ROOM_STATUSES,
            )
        } returns count
    }

    private fun assertValidationFails(errorType: CoreErrorType, action: () -> Unit) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
