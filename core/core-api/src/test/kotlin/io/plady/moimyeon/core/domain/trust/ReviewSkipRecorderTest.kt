package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.ReviewSkipEntity
import io.plady.moimyeon.storage.db.core.ReviewSkipRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.UUID

class ReviewSkipRecorderTest {
    private val skipRepository = mockk<ReviewSkipRepository>()
    private val recorder = ReviewSkipRecorder(skipRepository)
    private val command = ReviewSkipCommand(
        roomId = UUID.randomUUID(),
        authorMemberId = UUID.randomUUID(),
        targetMemberId = UUID.randomUUID(),
    )

    @BeforeEach
    fun setUp() {
        every {
            skipRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberId(
                command.roomId,
                command.authorMemberId,
                command.targetMemberId,
            )
        } returns false
    }

    @Test
    fun `대상별 건너뛰기를 한 번 기록한다`() {
        val skipSlot = slot<ReviewSkipEntity>()
        every { skipRepository.saveAndFlush(capture(skipSlot)) } answers { firstArg() }

        recorder.record(command)

        assertThat(skipSlot.captured.roomId).isEqualTo(command.roomId)
        assertThat(skipSlot.captured.authorMemberId).isEqualTo(command.authorMemberId)
        assertThat(skipSlot.captured.targetMemberId).isEqualTo(command.targetMemberId)
    }

    @Test
    fun `이미 기록한 대상의 건너뛰기는 추가 저장하지 않는다`() {
        every {
            skipRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberId(
                command.roomId,
                command.authorMemberId,
                command.targetMemberId,
            )
        } returns true

        recorder.record(command)

        verify(exactly = 0) { skipRepository.saveAndFlush(any()) }
    }

    @Test
    fun `동시 건너뛰기의 유니크 충돌은 성공으로 처리한다`() {
        every { skipRepository.saveAndFlush(any()) } throws DataIntegrityViolationException(
            "건너뛰기 중복",
            SQLException("uk_review_skip_room_author_target"),
        )

        assertThatCode { recorder.record(command) }.doesNotThrowAnyException()
    }

    @Test
    fun `건너뛰기 유니크와 무관한 무결성 위반은 그대로 전파한다`() {
        val unexpected = DataIntegrityViolationException(
            "필수값 누락",
            SQLException("NULL not allowed for column room_id"),
        )
        every { skipRepository.saveAndFlush(any()) } throws unexpected

        assertThatThrownBy { recorder.record(command) }.isSameAs(unexpected)
    }
}
