package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.auth.ApiResponseAuthErrorWriter
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.security.auth.ApiResponseAuthenticationEntryPoint
import io.plady.moimyeon.security.auth.HeaderOrCookieBearerTokenResolver
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class MemberControllerTest : RestDocsTest() {
    private lateinit var memberService: MemberService
    private lateinit var profileService: ProfileService
    private lateinit var catalogService: CatalogService

    private val member: Member =
        Member.register(SocialLoginProvider.GOOGLE, "google-sub-1", Email("user@example.com"), LocalDateTime.of(2026, 1, 1, 0, 0))
    private val memberId: UUID = member.id
    private val principal = Principal { memberId.toString() }

    private val memberMeSummary = "내 상태 조회"
    private val memberMeDescription =
        "인증된 회원의 상태와 프로필 완성 여부를 반환한다. 프로필 미작성이면 profileCompleted=false, profile=null 이다. " +
            "profile 은 필수 프로필 작성 응답의 data 와 동일한 모양이다. " +
            "FE 는 로그인 직후 이 값으로 최초 프로필 작성 모달 노출 여부를 판단한다. " +
            "액세스 토큰이 없거나 유효하지 않으면 401(E1102), 토큰은 유효하지만 회원이 조회되지 않으면(탈퇴 등) 404(E1006)로 응답한다."

    @BeforeEach
    fun setUp() {
        memberService = mockk()
        profileService = mockk()
        catalogService = mockk()
        mockMvc = mockController(
            MemberController(memberService, profileService, catalogService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun memberMe() {
        every { memberService.getMember(memberId) } returns member
        every { profileService.hasProfile(memberId) } returns false

        mockMvc.perform(get("/v1/members/me").principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "memberMe",
                    memberMeSummary,
                    memberMeDescription,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.email").type(JsonFieldType.STRING).description("대표 이메일"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("회원 상태 (ACTIVE | RESTRICTED | WITHDRAWN)"),
                        fieldWithPath("data.profileCompleted").type(JsonFieldType.BOOLEAN).description("필수 프로필 작성 완료 여부"),
                        fieldWithPath("data.profile").type(JsonFieldType.NULL).optional()
                            .description("프로필 (미작성이면 null, 작성 완료 시 필수 프로필 작성 응답의 data 와 동일 모양)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `memberMe 회원 조회 불가 E1006`() {
        every { memberService.getMember(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(get("/v1/members/me").principal(principal))
            .andExpect(status().isNotFound)
            .andDo(documentApi("memberMe-e1006", memberMeSummary, memberMeDescription, errorResponseFields()))
    }

    @Test
    fun `memberMe 유효하지 않은 토큰 E1102`() {
        val securedMockMvc = mockController(
            MemberController(memberService, profileService, catalogService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
            filters = listOf(resourceServerFilter()),
        )

        securedMockMvc.perform(get("/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-or-expired-token"))
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("memberMe-e1102", memberMeSummary, memberMeDescription, errorResponseFields()))
    }

    // 운영 필터 체인과 같은 조립: 리소스서버 필터 + 실제 EntryPoint/Writer.
    // 무효·만료 토큰은 이 필터가 401(E1102)을 쓰고, 토큰 부재는 LoginMemberArgumentResolver 가 같은 응답을 만든다.
    private fun resourceServerFilter(): BearerTokenAuthenticationFilter {
        val key = SecretKeySpec("restdocs-jwt-secret-key-32bytes!!".toByteArray(), "HmacSHA256")
        val filter = BearerTokenAuthenticationFilter(
            ProviderManager(JwtAuthenticationProvider(NimbusJwtDecoder.withSecretKey(key).build())),
        )
        filter.setBearerTokenResolver(HeaderOrCookieBearerTokenResolver())
        filter.setAuthenticationEntryPoint(ApiResponseAuthenticationEntryPoint(ApiResponseAuthErrorWriter(JsonMapper.builder().build())))
        return filter
    }
}
