package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.CreateProfileRequest
import io.plady.moimyeon.core.api.controller.v1.request.UpdateProfileRequest
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.catalog.Company
import io.plady.moimyeon.core.domain.profile.Nickname
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.security.Principal
import java.util.UUID

class ProfileControllerTest : RestDocsTest() {
    private lateinit var profileService: ProfileService
    private lateinit var catalogService: CatalogService
    private val memberId: UUID = UUID.randomUUID()
    private val principal = Principal { memberId.toString() }

    private val createProfileSummary = "필수 프로필 작성"
    private val createProfileDescription =
        "닉네임(필수)과 선택 정보를 받아 프로필을 생성하고 프로필 완성 상태로 전환한다. " +
            "직무·지역은 카탈로그 참조 id 를 받는다(/v1/job-roles, /v1/regions). " +
            "요청 형태 오류 400(E400), 닉네임 형식 위반 400(E1005), 존재하지 않는 직무/지역 400(E1301/E1302), " +
            "인증 정보 없음·무효 401(E1102), 닉네임 중복 409(E1007), 이미 작성된 경우 409(E1008), " +
            "필수 약관 미동의 409(E1201)로 응답한다."
    private val updateProfileSummary = "프로필 수정"
    private val updateProfileDescription =
        "마이페이지 수정 폼의 전체 교체 저장. 닉네임은 자신이 쓰던 값을 유지해도 되고, 변경 시 전체 중복을 확인한다. " +
            "직무·지역·관심 회사는 카탈로그 참조 id 를 받는다. " +
            "요청 형태 오류 400(E400), 닉네임 형식 위반 400(E1005), 존재하지 않는 직무/지역/회사 400(E1301/E1302/E1303), " +
            "인증 정보 없음·무효 401(E1102), 프로필 미작성 404(E1009), 닉네임 중복 409(E1007)로 응답한다."
    private val nicknameAvailabilitySummary = "닉네임 사용 가능 여부 확인"
    private val nicknameAvailabilityDescription =
        "닉네임의 전체 중복 여부를 확인한다. 형식 위반(길이·문자·금칙어)은 available=false 가 아니라 400(E1005)으로, " +
            "필수 쿼리 파라미터(nickname) 누락은 400(E400)으로 응답한다."

    private val validCreateRequest = CreateProfileRequest(
        nickname = "꼼꼼한 라쿤 34",
        jobRoleId = 1L,
        bio = "실전처럼 압박 질문을 주고받는 걸 좋아해요.",
        meetingPreference = MeetingPreference.BOTH,
        sigunguId = 1L,
    )
    private val validUpdateRequest = UpdateProfileRequest(
        nickname = "꼼꼼한 라쿤 34",
        jobRoleId = 2L,
        bio = "실전처럼 압박 질문을 주고받는 걸 좋아해요.",
        interestCompanyIds = listOf(1L, 2L),
        meetingPreference = MeetingPreference.OFFLINE,
        sigunguId = 1L,
    )

    @BeforeEach
    fun setUp() {
        profileService = mockk()
        catalogService = mockk()
        mockMvc = mockController(
            ProfileController(profileService, catalogService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
            validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() },
        )
    }

    private fun performCreate(request: CreateProfileRequest) = mockMvc.perform(
        post("/v1/members/me/profile")
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper().writeValueAsString(request)),
    )

    private fun performUpdate(request: UpdateProfileRequest) = mockMvc.perform(
        put("/v1/members/me/profile")
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper().writeValueAsString(request)),
    )

    @Test
    fun createProfile() {
        val request = validCreateRequest
        every { profileService.create(request.toProfile(memberId)) } returns request.toProfile(memberId)

        performCreate(request)
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "createProfile",
                    createProfileSummary,
                    createProfileDescription,
                    requestFields(
                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("닉네임 (필수, 전체 중복 불가)"),
                        fieldWithPath("jobRoleId").type(JsonFieldType.NUMBER).optional().description("직무 id (선택, /v1/job-roles)"),
                        fieldWithPath("bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택)"),
                        fieldWithPath("meetingPreference").type(JsonFieldType.STRING).optional()
                            .description("진행 방식 선호 (ONLINE | OFFLINE | BOTH, 선택)"),
                        fieldWithPath("sigunguId").type(JsonFieldType.NUMBER).optional().description("선호 지역 시군구 id (선택, /v1/regions)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임"),
                        fieldWithPath("data.jobRoleId").type(JsonFieldType.NUMBER).optional().description("직무 id (선택)"),
                        fieldWithPath("data.bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택)"),
                        fieldWithPath("data.meetingPreference").type(JsonFieldType.STRING).optional()
                            .description("진행 방식 선호 (ONLINE | OFFLINE | BOTH, 선택)"),
                        fieldWithPath("data.sigunguId").type(JsonFieldType.NUMBER).optional().description("선호 지역 시군구 id (선택)"),
                        fieldWithPath("data.interestCompanies").type(JsonFieldType.ARRAY)
                            .description("관심 회사 (최초 작성 시 빈 배열, 수정에서 입력)"),
                        fieldWithPath("data.profileCompleted").type(JsonFieldType.BOOLEAN).description("필수 프로필 작성 완료 여부"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `createProfile 인증 없음 E1102`() {
        mockMvc.perform(
            post("/v1/members/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(validCreateRequest)),
        )
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("createProfile-e1102", createProfileSummary, createProfileDescription, errorResponseFields()))
    }

    @Test
    fun `createProfile 요청 형태 오류 E400`() {
        performCreate(validCreateRequest.copy(bio = "가".repeat(501)))
            .andExpect(status().isBadRequest)
            .andDo(documentApi("createProfile-e400", createProfileSummary, createProfileDescription, errorResponseFields()))
    }

    @Test
    fun `createProfile 닉네임 형식 위반 E1005`() {
        performCreate(validCreateRequest.copy(nickname = "금지문자!@#"))
            .andExpect(status().isBadRequest)
            .andDo(documentApi("createProfile-e1005", createProfileSummary, createProfileDescription, errorResponseFields()))
    }

    @Test
    fun `createProfile 존재하지 않는 직무 E1301`() {
        every { profileService.create(any()) } throws CoreException(CoreErrorType.JOB_ROLE_NOT_FOUND)

        performCreate(validCreateRequest)
            .andExpect(status().isBadRequest)
            .andDo(documentApi("createProfile-e1301", createProfileSummary, createProfileDescription, errorResponseFields()))
    }

    @Test
    fun `createProfile 닉네임 중복 E1007`() {
        every { profileService.create(any()) } throws CoreException(CoreErrorType.NICKNAME_DUPLICATED)

        performCreate(validCreateRequest)
            .andExpect(status().isConflict)
            .andDo(documentApi("createProfile-e1007", createProfileSummary, createProfileDescription, errorResponseFields()))
    }

    @Test
    fun `createProfile 이미 작성된 프로필 E1008`() {
        every { profileService.create(any()) } throws CoreException(CoreErrorType.PROFILE_ALREADY_EXISTS)

        performCreate(validCreateRequest)
            .andExpect(status().isConflict)
            .andDo(documentApi("createProfile-e1008", createProfileSummary, createProfileDescription, errorResponseFields()))
    }

    @Test
    fun `createProfile 필수 약관 미동의 E1201`() {
        every { profileService.create(any()) } throws CoreException(CoreErrorType.TERMS_NOT_AGREED)

        performCreate(validCreateRequest)
            .andExpect(status().isConflict)
            .andDo(documentApi("createProfile-e1201", createProfileSummary, createProfileDescription, errorResponseFields()))
    }

    @Test
    fun updateProfile() {
        val request = validUpdateRequest
        every { profileService.update(request.toProfile(memberId)) } returns request.toProfile(memberId)
        every { catalogService.getCompanies(listOf(1L, 2L)) } returns listOf(Company(1L, "달빛페이"), Company(2L, "한빛커머스"))

        performUpdate(request)
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "updateProfile",
                    updateProfileSummary,
                    updateProfileDescription,
                    requestFields(
                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("닉네임 (필수, 전체 중복 불가 — 자신 제외)"),
                        fieldWithPath("jobRoleId").type(JsonFieldType.NUMBER).optional().description("직무 id (선택, /v1/job-roles)"),
                        fieldWithPath("bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택)"),
                        fieldWithPath("interestCompanyIds").type(JsonFieldType.ARRAY).optional()
                            .description("관심 회사 id 목록 (선택, /v1/companies 검색 — 전체 교체)"),
                        fieldWithPath("meetingPreference").type(JsonFieldType.STRING).optional()
                            .description("진행 방식 선호 (ONLINE | OFFLINE | BOTH, 선택)"),
                        fieldWithPath("sigunguId").type(JsonFieldType.NUMBER).optional().description("선호 지역 시군구 id (선택, /v1/regions)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임"),
                        fieldWithPath("data.jobRoleId").type(JsonFieldType.NUMBER).optional().description("직무 id (선택)"),
                        fieldWithPath("data.bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택)"),
                        fieldWithPath("data.meetingPreference").type(JsonFieldType.STRING).optional()
                            .description("진행 방식 선호 (ONLINE | OFFLINE | BOTH, 선택)"),
                        fieldWithPath("data.sigunguId").type(JsonFieldType.NUMBER).optional().description("선호 지역 시군구 id (선택)"),
                        fieldWithPath("data.interestCompanies").type(JsonFieldType.ARRAY).description("관심 회사"),
                        fieldWithPath("data.interestCompanies[].companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.interestCompanies[].name").type(JsonFieldType.STRING).description("회사명"),
                        fieldWithPath("data.profileCompleted").type(JsonFieldType.BOOLEAN).description("필수 프로필 작성 완료 여부"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `updateProfile 인증 없음 E1102`() {
        mockMvc.perform(
            put("/v1/members/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(validUpdateRequest)),
        )
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("updateProfile-e1102", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    @Test
    fun `updateProfile 존재하지 않는 회사 E1303`() {
        every { profileService.update(any()) } throws CoreException(CoreErrorType.COMPANY_NOT_FOUND)

        performUpdate(validUpdateRequest)
            .andExpect(status().isBadRequest)
            .andDo(documentApi("updateProfile-e1303", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    @Test
    fun `updateProfile 닉네임 중복 E1007`() {
        every { profileService.update(any()) } throws CoreException(CoreErrorType.NICKNAME_DUPLICATED)

        performUpdate(validUpdateRequest)
            .andExpect(status().isConflict)
            .andDo(documentApi("updateProfile-e1007", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    @Test
    fun `updateProfile 프로필 미작성 E1009`() {
        every { profileService.update(any()) } throws CoreException(CoreErrorType.PROFILE_NOT_FOUND)

        performUpdate(validUpdateRequest)
            .andExpect(status().isNotFound)
            .andDo(documentApi("updateProfile-e1009", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    @Test
    fun nicknameSuggestion() {
        every { profileService.suggestNickname() } returns Nickname("명랑한 알파카 42")

        mockMvc.perform(get("/v1/nicknames/suggestion"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "nicknameSuggestion",
                    "닉네임 자동 추천",
                    "중복되지 않는 닉네임을 새로 생성해 반환한다. 최초 프로필 모달의 프리필과 ↻ 새로 만들기 재생성에서 사용한다.",
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
        every { profileService.isNicknameAvailable("차분한 펭귄 12") } returns true

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
        every { profileService.isNicknameAvailable("금지문자!@#") } throws CoreException(CoreErrorType.INVALID_NICKNAME)

        mockMvc.perform(get("/v1/nicknames/availability").param("nickname", "금지문자!@#"))
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi("nicknameAvailability-e1005", nicknameAvailabilitySummary, nicknameAvailabilityDescription, errorResponseFields()),
            )
    }
}
