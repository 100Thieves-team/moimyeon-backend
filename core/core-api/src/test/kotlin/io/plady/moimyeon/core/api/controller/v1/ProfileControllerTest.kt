package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.UpdateProfileRequest
import io.plady.moimyeon.core.api.facade.ProfileFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class ProfileControllerTest : RestDocsTest() {
    private lateinit var profileService: ProfileService
    private lateinit var companyService: CompanyService
    private val memberId: UUID = UUID.randomUUID()
    private val principal = Principal { memberId.toString() }

    private val updateProfileSummary = "프로필 수정"
    private val updateProfileDescription =
        "프로필 전체 교체 저장. 프로필은 가입 시 빈 상태로 함께 만들어져 회원당 항상 하나 존재하므로 별도 생성 API 는 없다. " +
            "아직 안 채운 값은 null 이 아니라 빈 문자열·UNSPECIFIED 로 오간다(지역만 미선택이 null). " +
            "관심 직무·지역·관심 회사는 선택 가능한 참조 id 를 받는다. 관심 회사는 검증 완료된 회사만 선택할 수 있다. " +
            "요청 형태 오류 400(E400), 존재하지 않는 직무/지역 또는 선택할 수 없는 회사 400(E1301/E1302/E1303), " +
            "인증 정보 없음·무효 401(E1102), 프로필을 찾을 수 없음 404(E1009)로 응답한다."

    private val validUpdateRequest = UpdateProfileRequest(
        interestJobRoleIds = listOf(1L, 2L),
        bio = "실전처럼 압박 질문을 주고받는 걸 좋아해요.",
        interestCompanyIds = listOf(1L, 2L),
        meetingPreference = MeetingPreference.OFFLINE,
        sigunguId = 1L,
    )

    @BeforeEach
    fun setUp() {
        profileService = mockk()
        companyService = mockk()
        mockMvc = mockController(
            ProfileController(ProfileFacade(profileService, companyService)),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    private fun performUpdate(request: UpdateProfileRequest) = mockMvc.perform(
        put("/v1/members/me/profile")
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper().writeValueAsString(request)),
    )

    @Test
    fun updateProfile() {
        val request = validUpdateRequest
        every { profileService.update(memberId, request.toContent()) } returns memberId
        every { profileService.getProfile(memberId) } returns request.toContent().toProfileFixture(memberId)
        every { companyService.getCompanies(listOf(1L, 2L)) } returns listOf(Company(1L, "달빛페이"), Company(2L, "한빛커머스"))

        performUpdate(request)
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"interestJobRoleIds\":[1,2]")
                    .contains("\"interestCompanies\":[{\"companyId\":1,\"name\":\"달빛페이\"},{\"companyId\":2,\"name\":\"한빛커머스\"}]")
                    .doesNotContain("\"interviewStage\"")
            }
            .andDo(
                documentApi(
                    "updateProfile",
                    updateProfileSummary,
                    updateProfileDescription,
                    requestFields(
                        fieldWithPath("interestJobRoleIds").type(JsonFieldType.ARRAY).optional()
                            .description("관심 직무 id 목록 (미지정이면 빈 배열, /v1/job-roles — 전체 교체)"),
                        fieldWithPath("bio").type(JsonFieldType.STRING).optional().description("자기소개 (미지정이면 빈 문자열, 최대 500자)"),
                        // 스칼라 배열의 아이템 타입(number)은 생성기가 필드 문서화로 표현하지 못해
                        // openapi3 태스크 후처리에서 보정한다 (build.gradle.kts patchGeneratedSchemas)
                        fieldWithPath("interestCompanyIds").type(JsonFieldType.ARRAY).optional()
                            .description("관심 회사 id 목록 (미지정이면 빈 배열, /v1/companies 검색 — 전체 교체)"),
                        fieldWithPath("meetingPreference").type(JsonFieldType.STRING).optional()
                            .description("진행 방식 선호 (UNSPECIFIED | ONLINE | OFFLINE | BOTH, 미지정이면 UNSPECIFIED)"),
                        fieldWithPath("sigunguId").type(JsonFieldType.NUMBER).optional()
                            .description("선호 지역 시군구 id (미선택이면 null, /v1/regions)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.interestJobRoleIds").type(JsonFieldType.ARRAY).description("관심 직무 id 목록 (미지정이면 빈 배열)"),
                        fieldWithPath("data.bio").type(JsonFieldType.STRING).description("자기소개 (미지정이면 빈 문자열)"),
                        fieldWithPath("data.meetingPreference").type(JsonFieldType.STRING)
                            .description("진행 방식 선호 (UNSPECIFIED | ONLINE | OFFLINE | BOTH)"),
                        fieldWithPath("data.sigunguId").type(JsonFieldType.NUMBER).optional().description("선호 지역 시군구 id (미선택이면 null)"),
                        fieldWithPath("data.interestCompanies").type(JsonFieldType.ARRAY).description("관심 회사 (미지정이면 빈 배열)"),
                        fieldWithPath("data.interestCompanies[].companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.interestCompanies[].name").type(JsonFieldType.STRING).description("회사명"),
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
    fun `updateProfile 요청 형태 오류 E400`() {
        performUpdate(validUpdateRequest.copy(bio = "가".repeat(501)))
            .andExpect(status().isBadRequest)
            .andDo(documentApi("updateProfile-e400", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    @Test
    fun `updateProfile 존재하지 않는 직무 E1301`() {
        every { profileService.update(memberId, any()) } throws CoreException(CoreErrorType.JOB_ROLE_NOT_FOUND)

        performUpdate(validUpdateRequest)
            .andExpect(status().isBadRequest)
            .andDo(documentApi("updateProfile-e1301", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    @Test
    fun `updateProfile 선택할 수 없는 회사 E1303`() {
        every { profileService.update(memberId, any()) } throws CoreException(CoreErrorType.COMPANY_NOT_FOUND)

        performUpdate(validUpdateRequest)
            .andExpect(status().isBadRequest)
            .andDo(documentApi("updateProfile-e1303", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    @Test
    fun `updateProfile 프로필을 찾을 수 없음 E1009`() {
        every { profileService.update(memberId, any()) } throws CoreException(CoreErrorType.PROFILE_NOT_FOUND)

        performUpdate(validUpdateRequest)
            .andExpect(status().isNotFound)
            .andDo(documentApi("updateProfile-e1009", updateProfileSummary, updateProfileDescription, errorResponseFields()))
    }

    // Service 반환(조회 표현) 픽스처: 요청 내용과 같은 값의 도메인 객체
    private fun ProfileContent.toProfileFixture(memberId: UUID): MemberProfile = MemberProfile(
        memberId = memberId,
        bio = bio,
        meetingPreference = meetingPreference,
        sigunguId = sigunguId,
        interestJobRoleIds = interestJobRoleIds,
        interestCompanyIds = interestCompanyIds,
    )
}
