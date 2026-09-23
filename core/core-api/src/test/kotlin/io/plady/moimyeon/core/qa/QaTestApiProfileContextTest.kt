package io.plady.moimyeon.core.qa

import io.mockk.mockk
import io.plady.moimyeon.core.api.auth.DevAccessTokenIssuer
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.MemberRegistrationManager
import io.plady.moimyeon.core.qa.controller.QaTestDataController
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ResumeRepository
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock

@Tag("context")
class QaTestApiProfileContextTest {
    private val qaBeans = listOf(
        QaTestDataController::class.java,
        QaTestDataService::class.java,
        QaRoomFinder::class.java,
        QaRoomEraser::class.java,
        QaMemberResetter::class.java,
        QaMemberFinder::class.java,
        QaMemberEraser::class.java,
        QaDataSweeper::class.java,
        QaRoomScheduler::class.java,
        QaMemberCreator::class.java,
        QaResumeSummaryCompleter::class.java,
    )

    private val contextRunner = ApplicationContextRunner()
        .withBean(MemberFinder::class.java, { mockk() })
        .withBean(MemberRegistrationManager::class.java, { mockk() })
        .withBean(ResumeRepository::class.java, { mockk() })
        .withBean(MemberRepository::class.java, { mockk() })
        .withBean(Clock::class.java, { Clock.systemUTC() })
        .withBean(DevAccessTokenIssuer::class.java, { mockk() })
        .withBean(QaTestDataRepository::class.java, { mockk() })
        .withUserConfiguration(*qaBeans.toTypedArray())

    @Test
    fun `local과 local-dev와 dev 프로파일에서 QA Test API 빈을 등록한다`() {
        listOf("local", "local-dev", "dev").forEach { profile ->
            contextRunner.withPropertyValues("spring.profiles.active=$profile").run { context ->
                qaBeans.forEach { assertThat(context).hasSingleBean(it) }
            }
        }
    }

    @Test
    fun `staging 또는 live 프로파일이 함께 활성화되면 QA Test API 빈을 등록하지 않는다`() {
        listOf("dev,staging", "dev,live").forEach { profiles ->
            contextRunner.withPropertyValues("spring.profiles.active=$profiles").run { context ->
                qaBeans.forEach { assertThat(context).doesNotHaveBean(it) }
            }
        }
    }

    @Test
    fun `개발용이 아닌 프로파일에는 QA Test API 빈을 등록하지 않는다`() {
        listOf("staging", "live").forEach { profile ->
            contextRunner.withPropertyValues("spring.profiles.active=$profile").run { context ->
                qaBeans.forEach { assertThat(context).doesNotHaveBean(it) }
            }
        }
    }
}
