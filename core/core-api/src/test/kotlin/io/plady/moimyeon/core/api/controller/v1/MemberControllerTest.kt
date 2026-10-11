package io.plady.moimyeon.core.api.controller.v1

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.plady.moimyeon.core.api.auth.ApiResponseAuthErrorWriter
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.facade.MemberFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.security.auth.ApiResponseAuthenticationEntryPoint
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.HeaderOrCookieBearerTokenResolver
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
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
    private lateinit var companyService: CompanyService
    private lateinit var authCookieFactory: AuthCookieFactory

    private val member: Member = Member.register(
        SocialLoginProvider.GOOGLE,
        "google-sub-1",
        Email("user@example.com"),
        Nickname("차분한 펭귄 12"),
        LocalDateTime.of(2026, 1, 1, 0, 0),
    )
    private val memberId: UUID = member.id

    // 가입 시 만들어지는 빈 프로필
    private val profile = MemberProfile(
        memberId = memberId,
        bio = "",
        interestJobRoleIds = listOf(1L, 2L),
        interestCompanyIds = listOf(1L, 2L),
    )
    private val principal = Principal { memberId.toString() }

    private val memberMeSummary = "내 상태 조회"
    private val memberMeDescription =
        "인증된 회원의 상태와 프로필을 반환한다. 닉네임은 가입 시 자동 부여되는 회원 속성이다. " +
            "프로필은 가입 시 빈 상태로 함께 만들어져 회원당 항상 하나 존재한다 — 아직 안 채운 값은 " +
            "빈 문자열·빈 배열로 내려간다. " +
            "profile 은 프로필 수정 응답의 data 와 동일한 모양이다. " +
            "액세스 토큰이 없거나 유효하지 않으면 401(E1102), 토큰은 유효하지만 회원이 조회되지 않으면(탈퇴 등) 404(E1006)로 응답한다."
    private val withdrawSummary = "회원 탈퇴"
    private val withdrawDescription =
        "탈퇴한다(「회원 및 프로필」 §4.8). 요청 한 번으로 참가 신청 대기 건을 모두 철회하고, 참여 중인 모집 중·확정 룸에서 " +
            "나간다(방장이면 위임·모집 재개·취소, 참여자면 인원이 최소 밑으로 내려갈 때 모집 재개). 진행 예정 시각이 지난 확정 룸에는 " +
            "남는다. 모든 기기의 세션을 끝내고 웹 푸시 등록을 지운 뒤 ACCESS_TOKEN·REFRESH_TOKEN 쿠키를 만료(Set-Cookie)시킨다. " +
            "같은 Google 계정으로 다시 로그인하면 확인을 거쳐 복구할 수 있다. 이미 탈퇴했으면 아무것도 하지 않고 성공한다(멱등). " +
            "없는 회원이면 404(E1006). 룸을 나가는 사이 새 참여가 생겨 세 번 시도해도 끝내지 못하면 409(E1014)."
    private val nicknameSuggestionSummary = "닉네임 자동 추천"
    private val nicknameSuggestionDescription =
        "중복되지 않는 닉네임을 새로 생성해 반환한다. 닉네임 변경 폼의 ↻ 새로 만들기 재생성에서 사용한다."
    private val nicknameAvailabilitySummary = "닉네임 사용 가능 여부 확인"
    private val nicknameAvailabilityDescription =
        "닉네임의 전체 중복 여부를 확인한다. 형식 위반(길이·문자·금칙어)은 available=false 가 아니라 400(E1005)으로, " +
            "필수 쿼리 파라미터(nickname) 누락은 400(E400)으로 응답한다."

    @BeforeEach
    fun setUp() {
        memberService = mockk()
        profileService = mockk()
        companyService = mockk()
        authCookieFactory = mockk()
        mockMvc = mockController(
            MemberController(memberService, MemberFacade(memberService, profileService, companyService), authCookieFactory),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun memberMe() {
        every { memberService.getMember(memberId) } returns member
        every { profileService.getProfile(memberId) } returns profile
        every { memberService.getAnalyticsId(memberId) } returns "0123456789abcdef0123456789abcdef"
        every { companyService.getCompanies(listOf(1L, 2L)) } returns listOf(
            Company(1L, "달빛페이"),
            Company(2L, "한빛커머스"),
        )

        mockMvc.perform(get("/v1/members/me").principal(principal))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"interestJobRoleIds\":[1,2]")
                    .contains("\"interestCompanies\":[{\"companyId\":1,\"name\":\"달빛페이\"},{\"companyId\":2,\"name\":\"한빛커머스\"}]")
                    .doesNotContain("\"meetingPreference\"")
                    .doesNotContain("\"sigunguId\"")
                    .doesNotContain("\"interviewStage\"")
                    .contains("\"analyticsId\":\"0123456789abcdef0123456789abcdef\"")
            }
            .andDo(
                documentApi(
                    "memberMe",
                    memberMeSummary,
                    memberMeDescription,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.email").type(JsonFieldType.STRING).description("대표 이메일"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임 (가입 시 자동 부여, 변경 가능)"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("회원 상태 (ACTIVE | RESTRICTED)"),
                        fieldWithPath("data.profile").type(JsonFieldType.OBJECT).description("프로필 (프로필 수정 응답의 data 와 동일 모양)"),
                        fieldWithPath("data.profile.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.profile.interestJobRoleIds").type(JsonFieldType.ARRAY).description("관심 직무 id 목록 (미지정이면 빈 배열)"),
                        fieldWithPath("data.profile.bio").type(JsonFieldType.STRING).description("한 줄 소개 (미지정이면 빈 문자열)"),
                        fieldWithPath("data.profile.interestCompanies").type(JsonFieldType.ARRAY).description("관심 회사 목록 (미지정이면 빈 배열)"),
                        fieldWithPath("data.profile.interestCompanies[].companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.profile.interestCompanies[].name").type(JsonFieldType.STRING).description("회사명"),
                        fieldWithPath("data.analyticsId").type(JsonFieldType.STRING).optional()
                            .description("분석 도구(PostHog) identify 에 쓰는 가명 회원 식별자. 32자리 소문자 16진수. 서버에 키가 없으면 null"),
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
            MemberController(memberService, MemberFacade(memberService, profileService, companyService), authCookieFactory),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
            filters = listOf(resourceServerFilter()),
        )

        securedMockMvc.perform(get("/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-or-expired-token"))
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("memberMe-e1102", memberMeSummary, memberMeDescription, errorResponseFields()))
    }

    @Test
    fun nicknameSuggestion() {
        every { memberService.suggestNickname() } returns Nickname("명랑한 알파카 42")

        mockMvc.perform(get("/v1/nicknames/suggestion"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "nicknameSuggestion",
                    nicknameSuggestionSummary,
                    nicknameSuggestionDescription,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("추천 닉네임 (중복 아님 보장)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun nicknameAvailability() {
        every { memberService.isNicknameAvailable("차분한 펭귄 12") } returns true

        mockMvc.perform(get("/v1/nicknames/availability").param("nickname", "차분한 펭귄 12"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "nicknameAvailability",
                    nicknameAvailabilitySummary,
                    nicknameAvailabilityDescription,
                    queryParameters(
                        parameterWithName("nickname").description("확인할 닉네임"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.available").type(JsonFieldType.BOOLEAN).description("사용 가능 여부 (중복이면 false)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `nicknameAvailability 파라미터 누락 E400`() {
        mockMvc.perform(get("/v1/nicknames/availability"))
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi("nicknameAvailability-e400", nicknameAvailabilitySummary, nicknameAvailabilityDescription, errorResponseFields()),
            )
    }

    @Test
    fun `nicknameAvailability 닉네임 형식 위반 E1005`() {
        every { memberService.isNicknameAvailable("금지문자!@#") } throws CoreException(CoreErrorType.INVALID_NICKNAME)

        mockMvc.perform(get("/v1/nicknames/availability").param("nickname", "금지문자!@#"))
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi("nicknameAvailability-e1005", nicknameAvailabilitySummary, nicknameAvailabilityDescription, errorResponseFields()),
            )
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

    @Test
    fun withdraw() {
        every { memberService.withdraw(memberId) } just Runs
        every { authCookieFactory.expireAccess() } returns
            ResponseCookie.from(AuthCookieFactory.ACCESS_TOKEN, "").path("/").maxAge(0).build()
        every { authCookieFactory.expireRefresh() } returns
            ResponseCookie.from(AuthCookieFactory.REFRESH_TOKEN, "").path(AuthCookieFactory.REFRESH_PATH).maxAge(0).build()

        mockMvc.perform(delete("/v1/members/me").principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "memberWithdraw",
                    withdrawSummary,
                    withdrawDescription,
                    responseHeaders(
                        headerWithName(HttpHeaders.SET_COOKIE).description("만료 처리된 ACCESS_TOKEN·REFRESH_TOKEN 쿠키"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `withdraw 없는 회원 E1006`() {
        every { memberService.withdraw(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(delete("/v1/members/me").principal(principal))
            .andExpect(status().isNotFound)
            .andDo(documentApi("memberWithdraw-e1006", withdrawSummary, withdrawDescription, errorResponseFields()))
    }

    @Test
    fun `withdraw 처리 중 새 참여 E1014`() {
        every { memberService.withdraw(memberId) } throws CoreException(CoreErrorType.MEMBER_WITHDRAWAL_INTERRUPTED)

        mockMvc.perform(delete("/v1/members/me").principal(principal))
            .andExpect(status().isConflict)
            .andDo(documentApi("memberWithdraw-e1014", withdrawSummary, withdrawDescription, errorResponseFields()))
    }
}
