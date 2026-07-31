package io.plady.moimyeon.core.api.controller.v1

import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.facade.MemberFacade
import io.plady.moimyeon.core.api.facade.ProfileFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.profile.ProfileService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.Principal
import java.util.UUID

// 어드바이스의 수송 계층 예외 계약: 본문 해석 실패·파라미터 누락·타입 불일치는 400(E400)으로 응답한다.
// 엔드포인트별 값 규칙(API 스펙)은 요청 DTO 의 toXxx() 가 확정하고 RestDocs 테스트가 문서화한다.
class ProfileControllerValidationTest {
    private lateinit var mockMvc: MockMvc
    private val profileService = mockk<ProfileService>()
    private val catalogService = mockk<CatalogService>()
    private val memberService = mockk<MemberService>()
    private val memberFacade = mockk<MemberFacade>()
    private val principal = Principal { UUID.randomUUID().toString() }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            ProfileController(profileService, ProfileFacade(profileService, catalogService)),
            MemberController(memberService, memberFacade),
            PublicProfileController(),
        )
            .setCustomArgumentResolvers(LoginMemberArgumentResolver())
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
    fun `깨진 JSON 은 400 E400 을 반환한다`() {
        val body = performCreate("""{"bio": """)
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        assertThat(body).contains("\"code\":\"E400\"")
    }

    @Test
    fun `필수 쿼리 파라미터가 없으면 400 E400 과 파라미터 정보를 반환한다`() {
        val body = mockMvc.perform(get("/v1/nicknames/availability"))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        assertThat(body).contains("\"code\":\"E400\"")
        assertThat(body).contains("\"nickname\"")
    }

    @Test
    fun `경로 변수 타입이 불일치하면 400 E400 을 반환한다`() {
        val body = mockMvc.perform(get("/v1/members/{memberId}/profile", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        assertThat(body).contains("\"code\":\"E400\"")
        assertThat(body).contains("\"memberId\"")
    }
}
