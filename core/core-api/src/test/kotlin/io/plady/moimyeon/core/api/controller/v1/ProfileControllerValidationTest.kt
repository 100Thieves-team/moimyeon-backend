package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.CreateProfileRequest
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.profile.ProfileService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.security.Principal
import java.util.UUID

// 수송 계층(요청 형태) 검증 계약: 검증 실패·본문 해석 실패는 400(E400)으로 응답한다.
class ProfileControllerValidationTest {
    private lateinit var mockMvc: MockMvc
    private val profileService = mockk<ProfileService>()
    private val catalogService = mockk<CatalogService>()
    private val principal = Principal { UUID.randomUUID().toString() }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ProfileController(profileService, catalogService))
            .setCustomArgumentResolvers(LoginMemberArgumentResolver())
            .setValidator(LocalValidatorFactoryBean().apply { afterPropertiesSet() })
            .setControllerAdvice(ApiControllerAdvice())
            .build()
    }

    private fun performCreate(body: String) = mockMvc.perform(
        post("/v1/members/me/profile")
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    @Test
    fun `스키마 길이를 넘는 선택 필드는 400 E400 과 필드 정보를 반환한다`() {
        val request = CreateProfileRequest(nickname = "차분한 펭귄 12", bio = "가".repeat(501))

        val body = performCreate(jsonMapper().writeValueAsString(request))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        assertThat(body).contains("\"result\":\"ERROR\"")
        assertThat(body).contains("\"code\":\"E400\"")
        assertThat(body).contains("\"bio\"")
    }

    @Test
    fun `필수 필드(nickname)가 없으면 400 E400 을 반환한다`() {
        val body = performCreate("""{"bio": "자기소개만 보냄"}""")
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        assertThat(body).contains("\"code\":\"E400\"")
    }

    @Test
    fun `깨진 JSON 은 400 E400 을 반환한다`() {
        val body = performCreate("""{"nickname": """)
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        assertThat(body).contains("\"code\":\"E400\"")
    }
}
