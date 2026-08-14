package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionCardSetReaderTest {
    private val participationFinder = mockk<ParticipationFinder>()
    private val questionRepository = mockk<QuestionRepository>()
    private val reader = QuestionCardSetReader(participationFinder, questionRepository)

    private val roomId = UUID.randomUUID()
    private val requesterMemberId = UUID.randomUUID()
    private val firstTargetMemberId = UUID.randomUUID()
    private val secondTargetMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `목록은 확정 시점 참여자에서 요청자만 제외하고 빈 카드셋까지 확정 명단 순서로 만든다`() {
        every { participationFinder.getConfirmedParticipantIds(roomId) } returns listOf(
            requesterMemberId,
            firstTargetMemberId,
            secondTargetMemberId,
        )
        every {
            questionRepository.findByRoomIdAndTargetMemberIdInAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                listOf(firstTargetMemberId, secondTargetMemberId),
            )
        } returns listOf(
            question(id = 1L, targetMemberId = secondTargetMemberId),
            question(id = 2L, targetMemberId = secondTargetMemberId, parentQuestionId = 1L),
        )

        val result = reader.getAllByRoomExceptTarget(roomId, requesterMemberId)

        assertThat(result.map { it.targetMemberId }).containsExactly(firstTargetMemberId, secondTargetMemberId)
        assertThat(result.first().questions).isEmpty()
        assertThat(result.last().questions.single().followUps.single().id).isEqualTo(2L)
        verify(exactly = 1) { participationFinder.getConfirmedParticipantIds(roomId) }
        verify(exactly = 1) {
            questionRepository.findByRoomIdAndTargetMemberIdInAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                listOf(firstTargetMemberId, secondTargetMemberId),
            )
        }
    }

    @Test
    fun `확정 시점 참여자가 요청자뿐이면 질문을 조회하지 않고 빈 목록을 반환한다`() {
        every { participationFinder.getConfirmedParticipantIds(roomId) } returns listOf(requesterMemberId)

        val result = reader.getAllByRoomExceptTarget(roomId, requesterMemberId)

        assertThat(result).isEmpty()
        verify(exactly = 0) {
            questionRepository.findByRoomIdAndTargetMemberIdInAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                any(),
                any(),
            )
        }
    }

    @Test
    fun `상세는 원 질문에 직접 꼬리질문을 붙이고 작성자와 출처를 보존한다`() {
        every {
            questionRepository.findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                firstTargetMemberId,
            )
        } returns listOf(
            question(id = 1L, targetMemberId = firstTargetMemberId, asked = true),
            question(
                id = 2L,
                targetMemberId = firstTargetMemberId,
                parentQuestionId = 1L,
                source = QuestionSource.IN_PROGRESS,
                asked = true,
            ),
        )

        val result = reader.getByRoomAndTarget(roomId, firstTargetMemberId)

        assertThat(result.targetMemberId).isEqualTo(firstTargetMemberId)
        val question = result.questions.single()
        assertThat(question.id).isEqualTo(1L)
        assertThat(question.authorMemberId).isEqualTo(authorMemberId)
        assertThat(question.content).isEqualTo("질문 1")
        assertThat(question.source).isEqualTo(QuestionSource.PREPARATION)
        assertThat(question.asked).isTrue()
        assertThat(question.followUps.single().source).isEqualTo(QuestionSource.IN_PROGRESS)
        assertThat(question.followUps.single().asked).isTrue()
    }

    @Test
    fun `유효한 대상에게 질문이 없으면 빈 카드셋을 반환한다`() {
        every {
            questionRepository.findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                firstTargetMemberId,
            )
        } returns emptyList()

        val result = reader.getByRoomAndTarget(roomId, firstTargetMemberId)

        assertThat(result).isEqualTo(QuestionCardSet(firstTargetMemberId, emptyList()))
    }

    @Test
    fun `부모가 조회되지 않은 꼬리질문은 독립 질문으로 노출하지 않는다`() {
        every {
            questionRepository.findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                firstTargetMemberId,
            )
        } returns listOf(
            question(id = 2L, targetMemberId = firstTargetMemberId, parentQuestionId = 1L),
        )

        val result = reader.getByRoomAndTarget(roomId, firstTargetMemberId)

        assertThat(result.questions).isEmpty()
    }

    @Test
    fun `본인 카드셋은 내용을 열지 않고 질문을 준비한 작성자 수만 조회한다`() {
        every {
            questionRepository.countDistinctAuthorsByRoomIdAndTargetMemberIdAndDeletedAtIsNull(
                roomId,
                requesterMemberId,
            )
        } returns 2L

        val result = reader.countPreparers(roomId, requesterMemberId)

        assertThat(result).isEqualTo(2)
    }

    private fun question(
        id: Long,
        targetMemberId: UUID,
        parentQuestionId: Long? = null,
        source: QuestionSource = QuestionSource.PREPARATION,
        asked: Boolean = false,
    ): QuestionEntity = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.roomId } returns this@QuestionCardSetReaderTest.roomId
        every { this@mockk.targetMemberId } returns targetMemberId
        every { this@mockk.authorMemberId } returns this@QuestionCardSetReaderTest.authorMemberId
        every { this@mockk.parentQuestionId } returns parentQuestionId
        every { this@mockk.content } returns "질문 $id"
        every { this@mockk.source } returns source
        every { this@mockk.asked } returns asked
    }
}
