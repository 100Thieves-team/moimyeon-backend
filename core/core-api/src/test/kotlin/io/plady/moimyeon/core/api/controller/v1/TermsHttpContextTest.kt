package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.TermsRepository
import jakarta.servlet.Filter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

class TermsHttpContextTest(
    private val webApplicationContext: WebApplicationContext,
    @Qualifier("springSecurityFilterChain") private val securityFilterChain: Filter,
    private val termsRepository: TermsRepository,
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
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("data").path("terms").path(0).path("version").asString()).isEqualTo("v1.0") }

        val terms = termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE).first()
        mockMvc.perform(get("/v1/terms/${terms.id}"))
            .andExpect(status().isOk)
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("data").path("content").asString()).isEqualTo(terms.content) }
    }

    @Test
    fun `적재한 두 긴 초안은 DB에 있지만 공개 API에서는 노출하지 않는다`() {
        val drafts = termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.DRAFT)
        assertThat(drafts).hasSize(2)
        drafts.forEach { draft ->
            assertThat(draft.version).isEqualTo("v1.1")
            assertThat(draft.effectiveFrom).isEqualTo(LocalDateTime.of(2026, 10, 8, 0, 0))
            assertThat(draft.content).contains("이유제", "010-9328-9628", "\n## ", "2026-10-08")
            assertThat(draft.content.toByteArray(Charsets.UTF_8).size).isBetween(15_000, 65_535)
            mockMvc.perform(get("/v1/terms/${draft.id}"))
                .andExpect(status().isNotFound)
                .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("error").path("code").asString()).isEqualTo("E1202") }
        }
    }

    @Test
    fun `공개 문서 경로의 쓰기 요청과 다른 하위 경로는 인증 없이 허용하지 않는다`() {
        mockMvc.perform(post("/v1/terms/019daf00-0000-7000-8000-000000000001"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/terms/internal/publication"))
            .andExpect(status().isUnauthorized)
    }
}
