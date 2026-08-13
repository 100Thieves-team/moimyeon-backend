package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.plady.moimyeon.core.api.auth.ApiResponseAuthErrorWriter
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.UpdateNicknameRequest
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
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.security.auth.ApiResponseAuthenticationEntryPoint
import io.plady.moimyeon.security.auth.HeaderOrCookieBearerTokenResolver
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
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

    private val member: Member = Member.register(
        SocialLoginProvider.GOOGLE,
        "google-sub-1",
        Email("user@example.com"),
        Nickname("차분한 펭귄 12"),
        LocalDateTime.of(2026, 1, 1, 0, 0),
    )
    private val memberId: UUID = member.id

    // 가입 시 만들어지는 빈 프로필 — 미지정은 null 이 아니라 값이다
    private val profile = MemberProfile(
        memberId = memberId,
        bio = "",
        meetingPreference = MeetingPreference.UNSPECIFIED,
        sigunguId = null,
        interestJobRoleIds = listOf(1L, 2L),
        interestCompanyIds = listOf(1L, 2L),
    )
    private val principal = Principal { memberId.toString() }

    private val memberMeSummary = "내 상태 조회"
    private val memberMeDescription =
        "인증된 회원의 상태와 프로필을 반환한다. 닉네임은 가입 시 자동 부여되는 회원 속성이다. " +
            "프로필은 가입 시 빈 상태로 함께 만들어져 회원당 항상 하나 존재한다 — 아직 안 채운 값은 " +
            "null 이 아니라 빈 문자열·UNSPECIFIED 로 내려간다(지역만 미선택이 null). " +
            "profile 은 프로필 수정 응답의 data 와 동일한 모양이다. " +
            "액세스 토큰이 없거나 유효하지 않으면 401(E1102), 토큰은 유효하지만 회원이 조회되지 않으면(탈퇴 등) 404(E1006)로 응답한다."
    private val updateNicknameSummary = "닉네임 변경"
    private val updateNicknameDescription =
        "회원의 닉네임을 변경한다. 자신이 쓰던 닉네임 유지는 허용하고, 변경 시 전체 중복을 확인한다. " +
            "형식 위반 400(E1005), 인증 정보 없음·무효 401(E1102), 닉네임 중복 409(E1007)로 응답한다."
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
        mockMvc = mockController(
            MemberController(memberService, MemberFacade(memberService, profileService, companyService)),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun memberMe() {
        every { memberService.getMember(memberId) } returns member
        every { profileService.getProfile(memberId) } returns profile
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
                    .doesNotContain("\"interviewStage\"")
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
                        fieldWithPath("data.profile.meetingPreference").type(JsonFieldType.STRING).description("만남 선호 (UNSPECIFIED | ONLINE | OFFLINE | BOTH)"),
                        fieldWithPath("data.profile.sigunguId").type(JsonFieldType.NUMBER).optional().description("관심 지역 시군구 id (미선택이면 null)"),
                        fieldWithPath("data.profile.interestCompanies").type(JsonFieldType.ARRAY).description("관심 회사 목록 (미지정이면 빈 배열)"),
                        fieldWithPath("data.profile.interestCompanies[].companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.profile.interestCompanies[].name").type(JsonFieldType.STRING).description("회사명"),
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
            MemberController(memberService, MemberFacade(memberService, profileService, companyService)),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
            filters = listOf(resourceServerFilter()),
        )

        securedMockMvc.perform(get("/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-or-expired-token"))
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("memberMe-e1102", memberMeSummary, memberMeDescription, errorResponseFields()))
    }

    @Test
    fun updateNickname() {
        every { memberService.changeNickname(memberId, Nickname("명랑한 해달 33")) } just Runs

        mockMvc.perform(
            put("/v1/members/me/nickname")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(UpdateNicknameRequest("명랑한 해달 33"))),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "updateNickname",
                    updateNicknameSummary,
                    updateNicknameDescription,
                    requestFields(
                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("변경할 닉네임 (전체 중복 불가 — 자신 제외)"),
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
    fun `updateNickname 형식 위반 E1005`() {
        mockMvc.perform(
            put("/v1/members/me/nickname")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(UpdateNicknameRequest("금지문자!@#"))),
        )
            .andExpect(status().isBadRequest)
            .andDo(documentApi("updateNickname-e1005", updateNicknameSummary, updateNicknameDescription, errorResponseFields()))
    }

    @Test
    fun `updateNickname 닉네임 중복 E1007`() {
        every { memberService.changeNickname(memberId, Nickname("명랑한 해달 33")) } throws CoreException(CoreErrorType.NICKNAME_DUPLICATED)

        mockMvc.perform(
            put("/v1/members/me/nickname")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(UpdateNicknameRequest("명랑한 해달 33"))),
        )
            .andExpect(status().isConflict)
            .andDo(documentApi("updateNickname-e1007", updateNicknameSummary, updateNicknameDescription, errorResponseFields()))
    }

    @Test
    fun `updateNickname 인증 없음 E1102`() {
        mockMvc.perform(
            put("/v1/members/me/nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(UpdateNicknameRequest("명랑한 해달 33"))),
        )
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("updateNickname-e1102", updateNicknameSummary, updateNicknameDescription, errorResponseFields()))
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
}
