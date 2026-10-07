package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.terms.TermsAgreementManager
import io.plady.moimyeon.core.domain.terms.TermsFinder
import io.plady.moimyeon.core.domain.terms.TermsService
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import io.plady.moimyeon.storage.db.core.TermsRepository
import jakarta.servlet.Filter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Transactional
@Import(TermsHttpContextTest.PublicationClockConfiguration::class)
class TermsHttpContextTest(
    private val webApplicationContext: WebApplicationContext,
    @Qualifier("springSecurityFilterChain") private val securityFilterChain: Filter,
    private val termsRepository: TermsRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(securityFilterChain)
        mockMvc = builder.build()
    }

    @Test
    fun `비로그인으로 기존 목록과 시행된 본문을 DB에서 조회한다`() {
        mockMvc.perform(get("/v1/terms"))
            .andExpect(status().isOk)
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("data").path("terms").size()).isEqualTo(2) }
            .andExpect {
                val terms = JsonMapper.shared().readTree(it.response.contentAsByteArray).path("data").path("terms")
                assertThat(terms.path(0).path("version").asString()).isEqualTo("v1.1")
                assertThat(terms.path(1).path("version").asString()).isEqualTo("v1.1")
            }

        val terms = termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE).first { it.version == "v1.0" }
        mockMvc.perform(get("/v1/terms/${terms.id}"))
            .andExpect(status().isOk)
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("data").path("content").asString()).isEqualTo(terms.content) }
    }

    @Test
    fun `적재한 두 정식 본문은 시행 정각부터 비로그인으로 전문 조회한다`() {
        val published = termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE).filter { it.version == "v1.1" }
        assertThat(published).hasSize(2)
        published.forEach { terms ->
            assertThat(terms.effectiveFrom).isEqualTo(LocalDateTime.of(2026, 10, 8, 0, 0))
            assertThat(terms.content).contains("이유제", "010-9328-9628", "\n## ", "2026년 10월 8일")
            assertThat(terms.content).doesNotContain("미발행 검토안", "시행 예정일", "[내부 메모:")
            assertThat(terms.content.toByteArray(Charsets.UTF_8).size).isBetween(8_000, 65_535)
            mockMvc.perform(get("/v1/terms/${terms.id}"))
                .andExpect(status().isOk)
                .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("data").path("content").asString()).isEqualTo(terms.content) }
        }
    }

    @Test
    fun `실제 시드의 목록과 가입 기록은 시행 직전 v1_0에서 정각 이후 v1_1로 전환한다`() {
        listOf(
            "2026-10-07T14:59:59.999999Z" to "v1.0",
            "2026-10-07T15:00:00Z" to "v1.1",
            "2026-10-07T15:00:01Z" to "v1.1",
        ).forEachIndexed { index, (instant, version) ->
            val clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
            val current = TermsService(TermsFinder(termsRepository, clock)).getActiveTerms()
            assertThat(current.map { it.version }).containsExactly(version, version)
            val member = memberRepository.saveAndFlush(
                MemberEntity(
                    id = UUID.randomUUID(),
                    email = "terms-publication-$index@example.com",
                    nickname = "terms-publication-$index",
                    status = MemberStatus.ACTIVE,
                    lastLoginAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                ),
            )

            TermsAgreementManager(termsRepository, termsAgreementRepository, clock)
                .agreeRequired(member.id, member.lastLoginAt)

            assertThat(termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(member.id).map { it.termsId })
                .containsExactlyInAnyOrderElementsOf(current.map { it.id })
        }
    }

    @Test
    fun `공개 문서 경로의 쓰기 요청과 다른 하위 경로는 인증 없이 허용하지 않는다`() {
        mockMvc.perform(post("/v1/terms/019daf00-0000-7000-8000-000000000001"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/terms/internal/publication"))
            .andExpect(status().isUnauthorized)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PublicationClockConfiguration {
        @Bean
        @Primary
        fun termsPublicationClock(): Clock = Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneOffset.UTC)
    }
}
