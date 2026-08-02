package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.catalog.JobGroup
import io.plady.moimyeon.core.domain.catalog.JobRole
import io.plady.moimyeon.core.domain.catalog.Sido
import io.plady.moimyeon.core.domain.catalog.Sigungu
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CatalogControllerTest : RestDocsTest() {
    private lateinit var catalogService: CatalogService

    @BeforeEach
    fun setUp() {
        catalogService = mockk()
        mockMvc = mockController(
            CatalogController(catalogService),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun jobRoles() {
        every { catalogService.getJobCatalog() } returns listOf(
            JobGroup(
                id = 1L,
                code = "IT_개발",
                displayName = "IT·개발",
                roles = listOf(JobRole(1L, "서버_백엔드", "서버·백엔드"), JobRole(2L, "프론트엔드", "프론트엔드")),
            ),
        )

        mockMvc.perform(get("/v1/job-roles"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "jobRoles",
                    "직무 카탈로그 조회",
                    "프로필 직무 드롭다운 소스. 직군별로 그룹핑된 유효(미폐기) 직무 목록을 반환한다. 크롤러가 관리하는 참조 데이터다.",
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.groups").type(JsonFieldType.ARRAY).description("직군 목록"),
                        fieldWithPath("data.groups[].code").type(JsonFieldType.STRING).description("직군 코드"),
                        fieldWithPath("data.groups[].displayName").type(JsonFieldType.STRING).description("직군 표시명"),
                        fieldWithPath("data.groups[].roles").type(JsonFieldType.ARRAY).description("직군 내 직무 목록"),
                        fieldWithPath("data.groups[].roles[].jobRoleId").type(JsonFieldType.NUMBER).description("직무 id (프로필 저장에 사용)"),
                        fieldWithPath("data.groups[].roles[].code").type(JsonFieldType.STRING).description("직무 코드"),
                        fieldWithPath("data.groups[].roles[].displayName").type(JsonFieldType.STRING).description("직무 표시명"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun regions() {
        every { catalogService.getRegions() } returns listOf(
            Sido(
                id = 1L,
                name = "서울특별시",
                shortName = "서울",
                sigungus = listOf(Sigungu(1L, "강남구"), Sigungu(2L, "마포구")),
            ),
        )

        mockMvc.perform(get("/v1/regions"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "regions",
                    "지역 카탈로그 조회",
                    "프로필 선호 지역 드롭다운 소스. 시도별로 그룹핑된 유효(미폐기) 시군구 목록을 반환한다(법정동 기준). 크롤러가 관리하는 참조 데이터다.",
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.sidos").type(JsonFieldType.ARRAY).description("시도 목록"),
                        fieldWithPath("data.sidos[].name").type(JsonFieldType.STRING).description("시도 정식명칭"),
                        fieldWithPath("data.sidos[].shortName").type(JsonFieldType.STRING).description("시도 축약명 (표시용, 예: 서울)"),
                        fieldWithPath("data.sidos[].sigungus").type(JsonFieldType.ARRAY).description("시군구 목록"),
                        fieldWithPath("data.sidos[].sigungus[].sigunguId").type(JsonFieldType.NUMBER).description("시군구 id (프로필 저장에 사용)"),
                        fieldWithPath("data.sidos[].sigungus[].name").type(JsonFieldType.STRING).description("시군구 명칭"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
