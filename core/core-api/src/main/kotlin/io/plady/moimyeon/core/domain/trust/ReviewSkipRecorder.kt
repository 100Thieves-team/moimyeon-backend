package io.plady.moimyeon.core.domain.trust

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.event.growth.GrowthEvent
import io.plady.moimyeon.core.event.growth.GrowthEventCode
import io.plady.moimyeon.core.event.growth.GrowthEventPublisher
import io.plady.moimyeon.storage.db.core.ReviewSkipEntity
import io.plady.moimyeon.storage.db.core.ReviewSkipRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

private const val REVIEW_SKIP_UNIQUE_CONSTRAINT = "uk_review_skip_room_author_target"

@Component
class ReviewSkipRecorder(
    private val skipRepository: ReviewSkipRepository,
    private val growthEventPublisher: GrowthEventPublisher,
) {
    // @Transactional 을 두지 않는다. 동시 중복의 유니크 충돌을 삼키는데, 트랜잭션 안에서는 저장소 예외가
    // rollback-only 를 걸어 커밋이 UnexpectedRollbackException 으로 깨진다. 그로스 사건은 트랜잭션 밖이면 바로 기록된다.
    fun record(command: ReviewSkipCommand) {
        log.debug { "review-skip.recorder.record roomId=${command.roomId} authorMemberId=${command.authorMemberId} targetMemberId=${command.targetMemberId}" }
        if (skipRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberId(
                command.roomId,
                command.authorMemberId,
                command.targetMemberId,
            )
        ) {
            return
        }

        try {
            skipRepository.saveAndFlush(
                ReviewSkipEntity(
                    roomId = command.roomId,
                    authorMemberId = command.authorMemberId,
                    targetMemberId = command.targetMemberId,
                ),
            )
            growthEventPublisher.publish(GrowthEvent(GrowthEventCode.REVIEW_SKIPPED, command.authorMemberId, mapOf("roomId" to command.roomId)))
        } catch (exception: DataIntegrityViolationException) {
            if (!isDuplicatedSkip(exception)) throw exception
        }
    }

    private fun isDuplicatedSkip(exception: DataIntegrityViolationException): Boolean {
        return (exception.rootCause?.message ?: exception.message)
            .orEmpty()
            .contains(REVIEW_SKIP_UNIQUE_CONSTRAINT, ignoreCase = true)
    }
}
