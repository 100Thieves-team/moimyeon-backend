package io.plady.moimyeon.core.event.growth

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.support.logging.GrowthEventWriter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

// 바깥 트랜잭션 없이 실제 커밋·롤백을 본다(testing.md). 테스트 프로필은 앱 logger가 WARN이라
// 실제 GrowthEventWriter 의 logger 만 INFO로 올려 캡처한다. 별도 @Import 를 두지 않아 공용 컨텍스트를 그대로 쓴다.
@ResourceLock("logback")
class GrowthEventCommitIT(
    private val growthEventPublisher: GrowthEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) : ContextTest() {
    private val logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(GrowthEventWriter::class.java)
    private val appender = ListAppender<ILoggingEvent>()

    private var previousLevel: Level? = null

    @BeforeEach
    fun attach() {
        previousLevel = logger.level
        appender.start()
        logger.level = Level.INFO
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
        logger.level = previousLevel
        appender.stop()
    }

    @Test
    fun `트랜잭션이 커밋되면 사건을 한 번 기록한다`() {
        transactionTemplate.executeWithoutResult {
            growthEventPublisher.publish(GrowthEvent(GrowthEventCode.MEMBER_SIGNED_UP, UUID.randomUUID()))
        }

        assertThat(appender.list).hasSize(1)
    }

    @Test
    fun `트랜잭션이 롤백되면 사건을 기록하지 않는다`() {
        assertThatThrownBy {
            transactionTemplate.executeWithoutResult {
                growthEventPublisher.publish(GrowthEvent(GrowthEventCode.MEMBER_SIGNED_UP, UUID.randomUUID()))
                throw IllegalStateException("rollback")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(appender.list).isEmpty()
    }

    @Test
    fun `트랜잭션 밖에서 발행하면 이미 커밋된 쓰기로 보고 바로 기록한다`() {
        growthEventPublisher.publish(GrowthEvent(GrowthEventCode.REVIEW_SKIPPED, UUID.randomUUID()))

        assertThat(appender.list).hasSize(1)
    }
}
