package io.plady.moimyeon.core.domain.trust

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ReviewServiceTest {
    private val eligibilityValidator = mockk<ReviewEligibilityValidator>()
    private val submissionManager = mockk<ReviewSubmissionManager>()
    private val reviewEditor = mockk<ReviewEditor>()
    private val skipRecorder = mockk<ReviewSkipRecorder>()
    private val targetFinder = mockk<ReviewTargetFinder>()
    private val receivedReviewFinder = mockk<ReceivedReviewFinder>()
    private val service = ReviewService(
        eligibilityValidator,
        submissionManager,
        reviewEditor,
        skipRecorder,
        targetFinder,
        receivedReviewFinder,
    )
    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    @Test
    fun `완료 룸의 출석자가 다른 출석자에게 선택 태그와 텍스트 후기를 제출한다`() {
        val tags = mutableSetOf("시간 약속을 잘 지켜요", "좋은 질문을 해요")
        val command = ReviewSubmissionCommand(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
            tags = tags.toSet(),
            content = "꼬리질문이 날카로워서 실전처럼 연습할 수 있었어요.",
        )
        givenEligible(command)
        every { submissionManager.submit(command) } returns 1L

        val reviewId = service.submit(command)
        tags.clear()

        assertThat(reviewId).isEqualTo(1L)
        verify(exactly = 1) { submissionManager.submit(command) }
    }

    @Test
    fun `태그와 텍스트가 모두 없어도 빈 후기 제출로 기록한다`() {
        val command = command()
        givenEligible(command)
        every { submissionManager.submit(command) } returns 1L

        val reviewId = service.submit(command)

        assertThat(reviewId).isEqualTo(1L)
        verify(exactly = 1) { submissionManager.submit(command) }
    }

    @Test
    fun `진행되지 않았거나 취소된 룸에는 후기를 제출하지 않는다`() {
        assertEligibilityFails(CoreErrorType.REVIEW_NOT_AVAILABLE)
    }

    @Test
    fun `작성자가 출석하지 않은 룸에는 후기를 제출하지 않는다`() {
        assertEligibilityFails(CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED)
    }

    @Test
    fun `출석하지 않은 대상자에게 후기를 제출하지 않는다`() {
        assertEligibilityFails(CoreErrorType.REVIEW_TARGET_NOT_ATTENDED)
    }

    @Test
    fun `자기 자신에게 후기를 제출하지 않는다`() {
        val selfCommand = command(targetMemberId = authorMemberId)
        every {
            eligibilityValidator.validate(roomId, authorMemberId, authorMemberId)
        } throws
            CoreException(CoreErrorType.REVIEW_SELF_NOT_ALLOWED)

        assertThatThrownBy {
            service.submit(selfCommand)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_SELF_NOT_ALLOWED)
        }
        verify(exactly = 0) { submissionManager.submit(any()) }
    }

    @Test
    fun `후기를 제출할 때 룸의 최신 완료 상태를 확인한다`() {
        assertEligibilityFails(CoreErrorType.REVIEW_NOT_AVAILABLE)
    }

    @Test
    fun `결석에서 출석으로 정정된 작성자는 후기를 제출할 수 있다`() {
        assertSubmissionSucceeds()
    }

    @Test
    fun `결석에서 출석으로 정정된 대상자에게 후기를 제출할 수 있다`() {
        assertSubmissionSucceeds()
    }

    @Test
    fun `출석에서 결석으로 정정된 참여자는 후기를 제출하거나 받을 수 없다`() {
        assertEligibilityFails(CoreErrorType.REVIEW_TARGET_NOT_ATTENDED)
    }

    @Test
    fun `같은 룸의 같은 대상자에게 다시 제출하면 중복 후기를 저장하지 않는다`() {
        assertSubmissionFails(CoreErrorType.REVIEW_DUPLICATED)
    }

    @Test
    fun `동시 제출의 유니크 충돌도 중복 후기 오류로 전파한다`() {
        assertSubmissionFails(CoreErrorType.REVIEW_DUPLICATED)
    }

    @Test
    fun `제출된 후기는 작성자에게 즉시 제출됨으로 표시한다`() {
        val targets = listOf(ReviewTarget(targetMemberId, ReviewTargetStatus.SUBMITTED))
        every { targetFinder.getTargets(authorMemberId, roomId) } returns targets

        val result = service.getTargets(authorMemberId, roomId)

        assertThat(result.single().status).isEqualTo(ReviewTargetStatus.SUBMITTED)
    }

    @Test
    fun `제출 후 3시간 전에는 대상자의 받은 후기에 보이지 않는다`() {
        every { receivedReviewFinder.getAll(targetMemberId) } returns emptyList()

        val result = service.getReceivedReviews(targetMemberId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `제출 후 3시간이 지나면 대상자의 받은 후기에 노출할 수 있다`() {
        val reviews = listOf(receivedReview())
        every { receivedReviewFinder.getAll(targetMemberId) } returns reviews

        val result = service.getReceivedReviews(targetMemberId)

        assertThat(result).containsExactlyElementsOf(reviews)
    }

    @Test
    fun `받은 후기에서는 작성자와 룸 이름 및 일자를 알 수 없다`() {
        val review = receivedReview()
        every { receivedReviewFinder.getAll(targetMemberId) } returns listOf(review)

        val result = service.getReceivedReviews(targetMemberId).single()

        assertThat(result).isEqualTo(
            ReceivedReview(
                id = 1L,
                tags = setOf("피드백이 구체적이에요"),
                content = "안정적으로 진행해주셨어요.",
            ),
        )
    }

    @Test
    fun `숨김되거나 삭제된 후기는 받은 후기 조회에서 제외한다`() {
        every { receivedReviewFinder.getAll(targetMemberId) } returns emptyList()

        assertThat(service.getReceivedReviews(targetMemberId)).isEmpty()
    }

    @Test
    fun `탈퇴한 작성자의 후기도 대상자의 받은 후기에서 유지한다`() {
        val review = receivedReview()
        every { receivedReviewFinder.getAll(targetMemberId) } returns listOf(review)

        assertThat(service.getReceivedReviews(targetMemberId)).containsExactly(review)
    }

    @Test
    fun `제출 후 3시간 동안 작성자가 태그와 텍스트를 수정한다`() {
        val command = updateCommand()
        every { reviewEditor.update(command) } just Runs

        service.update(command)

        verify(exactly = 1) { reviewEditor.update(command) }
    }

    @Test
    fun `제출 후 3시간이 지나면 후기를 수정하지 않는다`() {
        assertUpdateFails(CoreErrorType.REVIEW_EDIT_WINDOW_CLOSED)
    }

    @Test
    fun `작성자가 아닌 사용자는 3시간 안에도 후기를 수정하지 않는다`() {
        assertUpdateFails(CoreErrorType.REVIEW_FORBIDDEN)
    }

    @Test
    fun `제출 후 3시간 동안 작성자가 후기를 삭제한다`() {
        every { reviewEditor.delete(authorMemberId, 1L) } just Runs

        service.delete(authorMemberId, 1L)

        verify(exactly = 1) { reviewEditor.delete(authorMemberId, 1L) }
    }

    @Test
    fun `제출 후 3시간이 지나면 후기를 삭제하지 않는다`() {
        assertDeleteFails(CoreErrorType.REVIEW_EDIT_WINDOW_CLOSED)
    }

    @Test
    fun `작성자가 아닌 사용자는 3시간 안에도 후기를 삭제하지 않는다`() {
        assertDeleteFails(CoreErrorType.REVIEW_FORBIDDEN)
    }

    @Test
    fun `삭제한 뒤에는 같은 룸의 같은 대상자에게 후기를 다시 제출할 수 있다`() {
        val submission = command()
        every { reviewEditor.delete(authorMemberId, 1L) } just Runs
        givenEligible(submission)
        every { submissionManager.submit(submission) } returns 2L

        service.delete(authorMemberId, 1L)
        val resubmittedReviewId = service.submit(submission)

        assertThat(resubmittedReviewId).isEqualTo(2L)
        verify(exactly = 1) { reviewEditor.delete(authorMemberId, 1L) }
        verify(exactly = 1) { submissionManager.submit(submission) }
    }

    @Test
    fun `후기는 대상자별로 개별 건너뛰기 기록한다`() {
        val command = skipCommand()
        givenEligible(command)
        every { skipRecorder.record(command) } just Runs

        service.skip(command)

        verify(exactly = 1) { skipRecorder.record(command) }
    }

    @Test
    fun `건너뛴 대상자에게 나중에 후기를 제출할 수 있다`() {
        val skip = skipCommand()
        val submission = command()
        givenEligible(skip)
        givenEligible(submission)
        every { skipRecorder.record(skip) } just Runs
        every { submissionManager.submit(submission) } returns 1L

        service.skip(skip)
        val reviewId = service.submit(submission)

        assertThat(reviewId).isEqualTo(1L)
        verify(exactly = 1) { skipRecorder.record(skip) }
        verify(exactly = 1) { submissionManager.submit(submission) }
    }

    @Test
    fun `한 대상자를 건너뛰어도 남은 대상자의 후기를 계속 작성할 수 있다`() {
        val remainingTargetMemberId = UUID.randomUUID()
        val skip = skipCommand()
        val remainingSubmission = command(remainingTargetMemberId)
        givenEligible(skip)
        givenEligible(remainingSubmission)
        every { skipRecorder.record(skip) } just Runs
        every { submissionManager.submit(remainingSubmission) } returns 1L

        service.skip(skip)
        val reviewId = service.submit(remainingSubmission)

        assertThat(reviewId).isEqualTo(1L)
        verify(exactly = 1) { submissionManager.submit(remainingSubmission) }
    }

    @Test
    fun `후기 작성 대상은 완료 룸 출석자 중 본인을 제외한 참여자다`() {
        val targets = listOf(
            ReviewTarget(targetMemberId, ReviewTargetStatus.WRITABLE),
            ReviewTarget(UUID.randomUUID(), ReviewTargetStatus.WRITABLE),
        )
        every { targetFinder.getTargets(authorMemberId, roomId) } returns targets

        val result = service.getTargets(authorMemberId, roomId)

        assertThat(result).isSameAs(targets)
        verify(exactly = 1) { targetFinder.getTargets(authorMemberId, roomId) }
    }

    @Test
    fun `이미 제출한 대상자는 제출됨이고 나머지는 작성 가능으로 표시한다`() {
        val targets = listOf(
            ReviewTarget(targetMemberId, ReviewTargetStatus.SUBMITTED),
            ReviewTarget(UUID.randomUUID(), ReviewTargetStatus.WRITABLE),
        )
        every { targetFinder.getTargets(authorMemberId, roomId) } returns targets

        val result = service.getTargets(authorMemberId, roomId)

        assertThat(result).containsExactlyElementsOf(targets)
    }

    @Test
    fun `건너뛴 대상자는 재진입했을 때 작성 가능으로 표시한다`() {
        val skip = skipCommand()
        val targets = listOf(ReviewTarget(targetMemberId, ReviewTargetStatus.WRITABLE))
        givenEligible(skip)
        every { skipRecorder.record(skip) } just Runs
        every { targetFinder.getTargets(authorMemberId, roomId) } returns targets

        service.skip(skip)
        val result = service.getTargets(authorMemberId, roomId)

        assertThat(result).containsExactlyElementsOf(targets)
    }

    private fun command(
        targetMemberId: UUID = this.targetMemberId,
    ): ReviewSubmissionCommand {
        return ReviewSubmissionCommand(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
            tags = emptySet(),
            content = null,
        )
    }

    private fun updateCommand(): ReviewUpdateCommand {
        return ReviewUpdateCommand(
            reviewId = 1L,
            authorMemberId = authorMemberId,
            tags = setOf("피드백이 구체적이에요"),
            content = "개선할 부분을 명확히 알려주셨어요.",
        )
    }

    private fun skipCommand(): ReviewSkipCommand {
        return ReviewSkipCommand(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
        )
    }

    private fun receivedReview(): ReceivedReview {
        return ReceivedReview(
            id = 1L,
            tags = setOf("피드백이 구체적이에요"),
            content = "안정적으로 진행해주셨어요.",
        )
    }

    private fun assertSubmissionSucceeds() {
        givenEligible(command())
        every { submissionManager.submit(command()) } returns 1L

        val reviewId = service.submit(command())

        assertThat(reviewId).isEqualTo(1L)
    }

    private fun assertSubmissionFails(errorType: CoreErrorType) {
        givenEligible(command())
        every { submissionManager.submit(command()) } throws CoreException(errorType)

        assertThatThrownBy {
            service.submit(command())
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(errorType)
        }
    }

    private fun assertEligibilityFails(errorType: CoreErrorType) {
        val command = command()
        every {
            eligibilityValidator.validate(command.roomId, command.authorMemberId, command.targetMemberId)
        } throws CoreException(errorType)

        assertThatThrownBy { service.submit(command) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
        verify(exactly = 0) { submissionManager.submit(any()) }
    }

    private fun givenEligible(command: ReviewSubmissionCommand) {
        every {
            eligibilityValidator.validate(command.roomId, command.authorMemberId, command.targetMemberId)
        } just Runs
    }

    private fun givenEligible(command: ReviewSkipCommand) {
        every {
            eligibilityValidator.validate(command.roomId, command.authorMemberId, command.targetMemberId)
        } just Runs
    }

    private fun assertUpdateFails(errorType: CoreErrorType) {
        val command = updateCommand()
        every { reviewEditor.update(command) } throws CoreException(errorType)

        assertThatThrownBy { service.update(command) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    private fun assertDeleteFails(errorType: CoreErrorType) {
        every { reviewEditor.delete(authorMemberId, 1L) } throws CoreException(errorType)

        assertThatThrownBy { service.delete(authorMemberId, 1L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
