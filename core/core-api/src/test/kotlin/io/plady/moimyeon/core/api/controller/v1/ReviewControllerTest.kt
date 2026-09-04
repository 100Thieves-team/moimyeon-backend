package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.ReceivedReviewResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReceivedReviewsResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewOverviewResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewOverviewWrittenReviewResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewTargetResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewTargetStatus
import io.plady.moimyeon.core.api.controller.v1.response.WrittenReviewResponse
import io.plady.moimyeon.core.api.facade.ReviewFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.trust.ReviewService
import io.plady.moimyeon.core.domain.trust.ReviewSkipContent
import io.plady.moimyeon.core.domain.trust.ReviewSubmissionContent
import io.plady.moimyeon.core.domain.trust.ReviewUpdateContent
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class ReviewControllerTest : RestDocsTest() {
    private lateinit var reviewFacade: ReviewFacade
    private lateinit var reviewService: ReviewService

    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000457")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val submittedTargetId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val writableTargetId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val principal = Principal { memberId.toString() }

    private val overviewSummary = "룸별 후기 작성 개요 조회"
    private val overviewDescription =
        "완료 룸의 출석자 중 본인을 제외한 후기 대상, 대상별 작성 상태와 작성자가 해당 룸에 제출한 후기를 조회한다. " +
            "미인증 E1102, 룸 없음 E1405, 완료 전 E2001, 작성자 결석 E2002로 응답한다."
    private val deprecatedTargetsSummary = "후기 작성 대상 조회 (Deprecated)"
    private val deprecatedTargetsDescription =
        "Deprecated: GET /v1/rooms/{roomId}/reviews/overview로 이전한다. " +
            "기존 클라이언트의 전환 기간에만 후기 대상과 제출 진행 상태를 이전 응답 계약으로 제공한다."
    private val submitSummary = "후기 제출"
    private val submitDescription =
        "완료 룸의 출석자가 다른 출석자에게 익명 여부, 선택 태그와 선택 텍스트 후기를 제출한다. " +
            "잘못된 태그 E400, 미인증 E1102, 룸 없음 E1405, 완료 전 E2001, 작성자 결석 E2002, 대상 결석 E2003, " +
            "본인 대상 E2004, 중복 제출 E2005로 응답한다."
    private val getReviewSummary = "작성한 후기 조회"
    private val getReviewDescription =
        "후기 작성자가 리뷰 id로 자신이 작성한 후기의 태그, 텍스트, 익명 여부를 조회한다. " +
            "수정 화면 진입 시 기존 내용을 채우는 용도이며 공개 기준 시각과 무관하게 조회할 수 있다. " +
            "미인증 E1102, 후기 없음 E2006, 작성자 불일치 E2007로 응답한다."
    private val updateSummary = "후기 수정"
    private val updateDescription =
        "후기 작성자가 공개 기준 시각 전까지 태그와 텍스트를 교체한다. " +
            "잘못된 태그 E400, 미인증 E1102, 후기 없음 E2006, 작성자 불일치 E2007, 수정 창 만료 E2008로 응답한다."
    private val deleteSummary = "후기 삭제"
    private val deleteDescription =
        "후기 작성자가 공개 기준 시각 전까지 후기를 삭제한다. 삭제 후 같은 대상에게 다시 제출할 수 있다. " +
            "미인증 E1102, 후기 없음 E2006, 작성자 불일치 E2007, 수정 창 만료 E2008로 응답한다."
    private val skipSummary = "후기 건너뛰기"
    private val skipDescription =
        "후기 대상을 개별로 건너뛴다. 건너뛴 뒤에도 다시 진입해 후기를 제출할 수 있다. " +
            "미인증 E1102, 룸 없음 E1405, 완료 전 E2001, 작성자 결석 E2002, 대상 결석 E2003, 본인 대상 E2004로 응답한다."
    private val receivedSummary = "내가 받은 후기 조회"
    private val receivedDescription =
        "공개 기준 시각이 지난 받은 후기만 마지막 후기 id 기반으로 조회한다. " +
            "익명 후기는 익명의 참여자, 공개 후기는 작성자 닉네임을 표시하며 룸 이름과 일자는 응답하지 않는다. " +
            "양수가 아닌 마지막 후기 id E400, 미인증 E1102로 응답한다."

    @BeforeEach
    fun setUp() {
        reviewFacade = mockk()
        reviewService = mockk()
        mockMvc = mockController(
            ReviewController(reviewFacade, reviewService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `완료 룸의 후기 작성 개요를 조회한다`() {
        every { reviewFacade.getOverview(memberId, roomId) } returns reviewOverviewResponse()

        mockMvc.perform(
            get("/v1/rooms/{roomId}/reviews/overview", roomId).principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "getReviewOverview",
                    overviewSummary,
                    overviewDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.submittedCount").type(JsonFieldType.NUMBER).description("제출 완료한 대상 수"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("후기 작성 대상 수"),
                        fieldWithPath("data.targets").type(JsonFieldType.ARRAY).description("후기 작성 대상"),
                        fieldWithPath("data.targets[].memberId").type(JsonFieldType.STRING).description("대상 회원 id"),
                        fieldWithPath("data.targets[].nickname").type(JsonFieldType.STRING).description("대상 닉네임"),
                        fieldWithPath("data.targets[].status").type(JsonFieldType.STRING)
                            .description("작성 상태 (WRITABLE | SUBMITTED)"),
                        fieldWithPath("data.reviews").type(JsonFieldType.ARRAY)
                            .description("작성자가 이 룸에 제출한 후기 (빈 배열 가능)"),
                        fieldWithPath("data.reviews[].reviewId").type(JsonFieldType.NUMBER)
                            .description("후기 id"),
                        fieldWithPath("data.reviews[].targetMemberId").type(JsonFieldType.STRING)
                            .description("후기 대상 회원 id"),
                        fieldWithPath("data.reviews[].tags").type(JsonFieldType.ARRAY)
                            .description("평가 태그 (빈 배열 가능)"),
                        fieldWithPath("data.reviews[].content").type(JsonFieldType.STRING)
                            .description("텍스트 후기 (작성하지 않았으면 빈 문자열)"),
                        fieldWithPath("data.reviews[].anonymous").type(JsonFieldType.BOOLEAN)
                            .description("작성자 닉네임 비공개 여부"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `deprecated 후기 대상 조회는 이전 응답 계약을 유지한다`() {
        every { reviewFacade.getOverview(memberId, roomId) } returns reviewOverviewResponse()

        mockMvc.perform(
            get("/v1/rooms/{roomId}/review-targets", roomId).principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentDeprecatedApi(
                    "getReviewTargets",
                    deprecatedTargetsSummary,
                    deprecatedTargetsDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.submittedCount").type(JsonFieldType.NUMBER).description("제출 완료한 대상 수"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("후기 작성 대상 수"),
                        fieldWithPath("data.targets").type(JsonFieldType.ARRAY).description("후기 작성 대상"),
                        fieldWithPath("data.targets[].memberId").type(JsonFieldType.STRING).description("대상 회원 id"),
                        fieldWithPath("data.targets[].nickname").type(JsonFieldType.STRING).description("대상 닉네임"),
                        fieldWithPath("data.targets[].status").type(JsonFieldType.STRING)
                            .description("작성 상태 (WRITABLE | SUBMITTED)"),
                        fieldWithPath("data.targets[].reviewId").type(JsonFieldType.NUMBER).optional()
                            .description("제출된 후기 id. WRITABLE 대상이면 null"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `닉네임 공개 여부를 선택해 참여자에게 태그와 한 줄 후기를 제출한다`() {
        val content = ReviewSubmissionContent(
            targetMemberId = writableTargetId,
            tags = setOf(
                "시간을 잘 지켜요",
                "준비가 성실해요",
                "질문이 날카로워요",
                "피드백이 구체적이에요",
                "소통이 원활해요",
            ),
            content = "꼬리질문이 날카로워서 실전 같았어요.",
            anonymous = false,
        )
        every { reviewService.submit(memberId, roomId, content) } returns 31L

        mockMvc.perform(
            post("/v1/rooms/{roomId}/reviews", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf(
                            "targetMemberId" to writableTargetId,
                            "tags" to listOf(
                                "시간을 잘 지켜요",
                                "준비가 성실해요",
                                "질문이 날카로워요",
                                "피드백이 구체적이에요",
                                "소통이 원활해요",
                            ),
                            "content" to "꼬리질문이 날카로워서 실전 같았어요.",
                            "anonymous" to false,
                        ),
                    ),
                ),
        )
            .andExpect(status().isCreated)
            .andDo(
                documentApi(
                    "submitReview",
                    submitSummary,
                    submitDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    requestFields(
                        fieldWithPath("targetMemberId").type(JsonFieldType.STRING).description("후기 대상 회원 id"),
                        fieldWithPath("tags").type(JsonFieldType.ARRAY)
                            .description(
                                "평가 태그 (시간을 잘 지켜요 | 준비가 성실해요 | 질문이 날카로워요 | " +
                                    "피드백이 구체적이에요 | 소통이 원활해요, 선택, 빈 배열 허용)",
                            ),
                        fieldWithPath("content").type(JsonFieldType.STRING).optional().description("한 줄 후기 (선택)"),
                        fieldWithPath("anonymous").type(JsonFieldType.BOOLEAN)
                            .optional()
                            .description("익명 작성 여부. true이면 익명의 참여자로 표시하며, 생략하면 true"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.reviewId").type(JsonFieldType.NUMBER).description("생성된 후기 id"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )

        verify(exactly = 1) { reviewService.submit(memberId, roomId, content) }
    }

    @Test
    fun `익명 여부를 생략하면 기존처럼 익명으로 후기를 제출한다`() {
        val content = ReviewSubmissionContent(
            targetMemberId = writableTargetId,
            tags = emptySet(),
            content = "",
            anonymous = true,
        )
        every { reviewService.submit(memberId, roomId, content) } returns 32L

        mockMvc.perform(
            post("/v1/rooms/{roomId}/reviews", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("targetMemberId" to writableTargetId))),
        )
            .andExpect(status().isCreated)

        verify(exactly = 1) { reviewService.submit(memberId, roomId, content) }
    }

    @Test
    fun `작성자가 리뷰 id로 자신이 작성한 후기를 조회한다`() {
        every { reviewFacade.getWrittenReview(memberId, 31L) } returns WrittenReviewResponse(
            reviewId = 31L,
            roomId = roomId,
            targetMemberId = submittedTargetId,
            targetNickname = "성실한 사슴 03",
            tags = listOf("시간을 잘 지켜요", "피드백이 구체적이에요"),
            content = "꼬리질문이 날카로워서 실전 같았어요.",
            anonymous = false,
        )

        mockMvc.perform(get("/v1/reviews/{reviewId}", 31L).principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "getReview",
                    getReviewSummary,
                    getReviewDescription,
                    pathParameters(parameterWithName("reviewId").description("조회할 후기 id")),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.reviewId").type(JsonFieldType.NUMBER).description("후기 id"),
                        fieldWithPath("data.roomId").type(JsonFieldType.STRING).description("후기가 작성된 룸 id"),
                        fieldWithPath("data.targetMemberId").type(JsonFieldType.STRING).description("후기 대상 회원 id"),
                        fieldWithPath("data.targetNickname").type(JsonFieldType.STRING)
                            .description("후기 대상 회원 닉네임. 탈퇴한 회원은 대체 표기로 내려간다"),
                        fieldWithPath("data.tags").type(JsonFieldType.ARRAY).description("평가 태그 (빈 배열 가능)"),
                        fieldWithPath("data.content").type(JsonFieldType.STRING)
                            .description("한 줄 후기 (작성하지 않았으면 빈 문자열)"),
                        fieldWithPath("data.anonymous").type(JsonFieldType.BOOLEAN).description("익명 작성 여부"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )

        verify(exactly = 1) { reviewFacade.getWrittenReview(memberId, 31L) }
    }

    @Test
    fun `인증 없이 작성한 후기를 조회하면 E1102`() {
        mockMvc.perform(get("/v1/reviews/{reviewId}", 31L))
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "getReview-e1102",
                    getReviewSummary,
                    getReviewDescription,
                    pathParameters(parameterWithName("reviewId").description("조회할 후기 id")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `작성한 후기 조회의 도메인 오류를 응답한다`() {
        listOf(
            CoreErrorType.REVIEW_NOT_FOUND,
            CoreErrorType.REVIEW_FORBIDDEN,
        ).forEach { errorType ->
            every { reviewFacade.getWrittenReview(memberId, 31L) } throws CoreException(errorType)

            mockMvc.perform(get("/v1/reviews/{reviewId}", 31L).principal(principal))
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getReview-${errorType.code.name.lowercase()}",
                        getReviewSummary,
                        getReviewDescription,
                        pathParameters(parameterWithName("reviewId").description("조회할 후기 id")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `공개 시각 전까지 작성한 후기를 수정한다`() {
        val content = ReviewUpdateContent(
            tags = setOf("소통이 원활해요"),
            content = "긴장을 풀어주는 진행이 좋았어요.",
        )
        justRun { reviewService.update(memberId, 31L, content) }

        mockMvc.perform(
            put("/v1/reviews/{reviewId}", 31L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf("tags" to listOf("소통이 원활해요"), "content" to "긴장을 풀어주는 진행이 좋았어요."),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "updateReview",
                    updateSummary,
                    updateDescription,
                    pathParameters(parameterWithName("reviewId").description("수정할 후기 id")),
                    requestFields(
                        fieldWithPath("tags").type(JsonFieldType.ARRAY)
                            .description(
                                "평가 태그 교체 (시간을 잘 지켜요 | 준비가 성실해요 | 질문이 날카로워요 | " +
                                    "피드백이 구체적이에요 | 소통이 원활해요, 빈 배열 허용)",
                            ),
                        fieldWithPath("content").type(JsonFieldType.STRING).optional().description("교체할 한 줄 후기 (선택)"),
                    ),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `작성한 후기를 삭제한다`() {
        justRun { reviewService.delete(memberId, 31L) }

        mockMvc.perform(delete("/v1/reviews/{reviewId}", 31L).principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "deleteReview",
                    deleteSummary,
                    deleteDescription,
                    pathParameters(parameterWithName("reviewId").description("삭제할 후기 id")),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `선택한 참여자의 후기를 건너뛴다`() {
        val content = ReviewSkipContent(writableTargetId)
        justRun { reviewService.skip(memberId, roomId, content) }

        mockMvc.perform(
            post("/v1/rooms/{roomId}/review-skips", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("targetMemberId" to writableTargetId))),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "skipReview",
                    skipSummary,
                    skipDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    requestFields(
                        fieldWithPath("targetMemberId").type(JsonFieldType.STRING).description("건너뛸 대상 회원 id"),
                    ),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `받은 후기는 익명 여부에 따른 작성자 표시명과 함께 조회한다`() {
        every { reviewFacade.getReceivedReviews(memberId, null, 20) } returns ReceivedReviewsResponse(
            reviews = listOf(
                ReceivedReviewResponse(
                    reviewId = 31L,
                    authorNickname = "익명의 참여자",
                    tags = listOf("시간을 잘 지켜요", "피드백이 구체적이에요"),
                    content = "덕분에 약점을 정확히 알았어요.",
                ),
            ),
            totalCount = 11,
            hasNext = true,
        )

        mockMvc.perform(
            get("/v1/members/me/received-reviews")
                .principal(principal)
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "getReceivedReviews",
                    receivedSummary,
                    receivedDescription,
                    queryParameters(
                        parameterWithName("lastReviewId").optional()
                            .description("직전 페이지 마지막 후기 id (선택). 첫 페이지는 생략한다"),
                        parameterWithName("size").optional()
                            .description("페이지 크기 (1~50, 기본 20). 범위 밖이면 기본값으로 조회한다"),
                    ),
                    receivedReviewResponseFields(),
                ),
            )

        verify(exactly = 1) { reviewFacade.getReceivedReviews(memberId, null, 20) }
    }

    @Test
    fun `범위 밖의 받은 후기 페이지 크기는 기본값으로 조회한다`() {
        every { reviewFacade.getReceivedReviews(memberId, 31L, 20) } returns ReceivedReviewsResponse(
            reviews = listOf(
                ReceivedReviewResponse(
                    reviewId = 31L,
                    authorNickname = "꼼꼼한 여우 12",
                    tags = listOf("시간을 잘 지켜요"),
                    content = "",
                ),
            ),
            totalCount = 1,
            hasNext = false,
        )

        mockMvc.perform(
            get("/v1/members/me/received-reviews")
                .principal(principal)
                .param("lastReviewId", "31")
                .param("size", "51"),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "getReceivedReviews-default-size",
                    receivedSummary,
                    receivedDescription,
                    queryParameters(
                        parameterWithName("lastReviewId")
                            .description("직전 페이지 마지막 후기 id"),
                        parameterWithName("size")
                            .description("페이지 크기. 허용 범위 1~50 밖이면 기본값 20을 사용한다"),
                    ),
                    receivedReviewResponseFields(),
                ),
            )

        verify(exactly = 1) { reviewFacade.getReceivedReviews(memberId, 31L, 20) }
    }

    @Test
    fun `받은 후기 마지막 id가 양수가 아니면 E400`() {
        mockMvc.perform(
            get("/v1/members/me/received-reviews")
                .principal(principal)
                .param("lastReviewId", "0"),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "getReceivedReviews-e400",
                    receivedSummary,
                    receivedDescription,
                    queryParameters(
                        parameterWithName("lastReviewId").description("양수가 아닌 마지막 후기 id"),
                    ),
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { reviewFacade.getReceivedReviews(any(), any(), any()) }
    }

    @Test
    fun `인증 없이 룸별 후기 작성 개요를 조회하면 E1102`() {
        mockMvc.perform(get("/v1/rooms/{roomId}/reviews/overview", roomId))
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "getReviewOverview-e1102",
                    overviewSummary,
                    overviewDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `인증 없이 deprecated 후기 작성 대상을 조회하면 E1102`() {
        mockMvc.perform(get("/v1/rooms/{roomId}/review-targets", roomId))
            .andExpect(status().isUnauthorized)
            .andDo(
                documentDeprecatedApi(
                    "getReviewTargets-e1102",
                    deprecatedTargetsSummary,
                    deprecatedTargetsDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `인증 없이 후기를 제출하면 E1102`() {
        mockMvc.perform(
            post("/v1/rooms/{roomId}/reviews", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("targetMemberId" to writableTargetId))),
        )
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "submitReview-e1102",
                    submitSummary,
                    submitDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `인증 없이 후기를 수정하면 E1102`() {
        mockMvc.perform(
            put("/v1/reviews/{reviewId}", 31L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("tags" to emptyList<String>()))),
        )
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "updateReview-e1102",
                    updateSummary,
                    updateDescription,
                    pathParameters(parameterWithName("reviewId").description("수정할 후기 id")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `인증 없이 후기를 삭제하면 E1102`() {
        mockMvc.perform(delete("/v1/reviews/{reviewId}", 31L))
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "deleteReview-e1102",
                    deleteSummary,
                    deleteDescription,
                    pathParameters(parameterWithName("reviewId").description("삭제할 후기 id")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `인증 없이 후기를 건너뛰면 E1102`() {
        mockMvc.perform(
            post("/v1/rooms/{roomId}/review-skips", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("targetMemberId" to writableTargetId))),
        )
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "skipReview-e1102",
                    skipSummary,
                    skipDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `룸별 후기 작성 개요 조회의 도메인 오류를 응답한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.REVIEW_NOT_AVAILABLE,
            CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED,
        ).forEach { errorType ->
            every { reviewFacade.getOverview(memberId, roomId) } throws CoreException(errorType)

            mockMvc.perform(get("/v1/rooms/{roomId}/reviews/overview", roomId).principal(principal))
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getReviewOverview-${errorType.code.name.lowercase()}",
                        overviewSummary,
                        overviewDescription,
                        pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `deprecated 후기 작성 대상 조회의 도메인 오류를 응답한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.REVIEW_NOT_AVAILABLE,
            CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED,
        ).forEach { errorType ->
            every { reviewFacade.getOverview(memberId, roomId) } throws CoreException(errorType)

            mockMvc.perform(get("/v1/rooms/{roomId}/review-targets", roomId).principal(principal))
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentDeprecatedApi(
                        "getReviewTargets-${errorType.code.name.lowercase()}",
                        deprecatedTargetsSummary,
                        deprecatedTargetsDescription,
                        pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `와이어프레임에 없는 평가 태그로 후기 제출은 E400`() {
        mockMvc.perform(
            post("/v1/rooms/{roomId}/reviews", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf("targetMemberId" to writableTargetId, "tags" to listOf("설명이 명확해요")),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "submitReview-e400",
                    submitSummary,
                    submitDescription,
                    pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { reviewService.submit(any(), any(), any()) }
    }

    @Test
    fun `후기 제출의 도메인 오류를 응답한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.REVIEW_NOT_AVAILABLE,
            CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED,
            CoreErrorType.REVIEW_TARGET_NOT_ATTENDED,
            CoreErrorType.REVIEW_SELF_NOT_ALLOWED,
            CoreErrorType.REVIEW_DUPLICATED,
        ).forEach { errorType ->
            val content = ReviewSubmissionContent(writableTargetId, emptySet(), "", anonymous = true)
            every { reviewService.submit(memberId, roomId, content) } throws CoreException(errorType)

            mockMvc.perform(
                post("/v1/rooms/{roomId}/reviews", roomId)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper().writeValueAsString(
                            mapOf("targetMemberId" to writableTargetId, "tags" to emptyList<String>()),
                        ),
                    ),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "submitReview-${errorType.code.name.lowercase()}",
                        submitSummary,
                        submitDescription,
                        pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `평가 태그가 공백이면 후기 수정은 E400`() {
        mockMvc.perform(
            put("/v1/reviews/{reviewId}", 31L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("tags" to listOf("   ")))),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "updateReview-e400",
                    updateSummary,
                    updateDescription,
                    pathParameters(parameterWithName("reviewId").description("수정할 후기 id")),
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { reviewService.update(any(), any(), any()) }
    }

    @Test
    fun `후기 수정의 도메인 오류를 응답한다`() {
        listOf(
            CoreErrorType.REVIEW_NOT_FOUND,
            CoreErrorType.REVIEW_FORBIDDEN,
            CoreErrorType.REVIEW_EDIT_WINDOW_CLOSED,
        ).forEach { errorType ->
            val content = ReviewUpdateContent(emptySet(), "")
            every { reviewService.update(memberId, 31L, content) } throws CoreException(errorType)

            mockMvc.perform(
                put("/v1/reviews/{reviewId}", 31L)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper().writeValueAsString(mapOf("tags" to emptyList<String>()))),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "updateReview-${errorType.code.name.lowercase()}",
                        updateSummary,
                        updateDescription,
                        pathParameters(parameterWithName("reviewId").description("수정할 후기 id")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `후기 삭제의 도메인 오류를 응답한다`() {
        listOf(
            CoreErrorType.REVIEW_NOT_FOUND,
            CoreErrorType.REVIEW_FORBIDDEN,
            CoreErrorType.REVIEW_EDIT_WINDOW_CLOSED,
        ).forEach { errorType ->
            every { reviewService.delete(memberId, 31L) } throws CoreException(errorType)

            mockMvc.perform(delete("/v1/reviews/{reviewId}", 31L).principal(principal))
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "deleteReview-${errorType.code.name.lowercase()}",
                        deleteSummary,
                        deleteDescription,
                        pathParameters(parameterWithName("reviewId").description("삭제할 후기 id")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `후기 건너뛰기의 도메인 오류를 응답한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.REVIEW_NOT_AVAILABLE,
            CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED,
            CoreErrorType.REVIEW_TARGET_NOT_ATTENDED,
            CoreErrorType.REVIEW_SELF_NOT_ALLOWED,
        ).forEach { errorType ->
            val content = ReviewSkipContent(writableTargetId)
            every { reviewService.skip(memberId, roomId, content) } throws CoreException(errorType)

            mockMvc.perform(
                post("/v1/rooms/{roomId}/review-skips", roomId)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper().writeValueAsString(mapOf("targetMemberId" to writableTargetId))),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "skipReview-${errorType.code.name.lowercase()}",
                        skipSummary,
                        skipDescription,
                        pathParameters(parameterWithName("roomId").description("완료된 룸 id (UUID)")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `인증 없이 받은 후기를 조회하면 E1102`() {
        mockMvc.perform(get("/v1/members/me/received-reviews"))
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "getReceivedReviews-e1102",
                    receivedSummary,
                    receivedDescription,
                    errorResponseFields(),
                ),
            )
    }

    private fun reviewOverviewResponse(): ReviewOverviewResponse {
        return ReviewOverviewResponse(
            submittedCount = 1,
            totalCount = 2,
            targets = listOf(
                ReviewTargetResponse(submittedTargetId, "꼼꼼한 여우 12", ReviewTargetStatus.SUBMITTED),
                ReviewTargetResponse(writableTargetId, "성실한 사슴 03", ReviewTargetStatus.WRITABLE),
            ),
            reviews = listOf(
                ReviewOverviewWrittenReviewResponse(
                    reviewId = 31L,
                    targetMemberId = submittedTargetId,
                    tags = listOf("시간을 잘 지켜요", "피드백이 구체적이에요"),
                    content = "꼬리질문이 날카로워서 실전 같았어요.",
                    anonymous = true,
                ),
            ),
        )
    }

    private fun receivedReviewResponseFields() = responseFields(
        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("공개 가능한 받은 후기 전체 수"),
        fieldWithPath("data.reviews").type(JsonFieldType.ARRAY).description("현재 페이지의 받은 후기"),
        fieldWithPath("data.reviews[].reviewId").type(JsonFieldType.NUMBER).description("후기 id"),
        fieldWithPath("data.reviews[].authorNickname").type(JsonFieldType.STRING)
            .description("작성자 표시명. 익명이면 익명의 참여자, 공개이면 현재 닉네임"),
        fieldWithPath("data.reviews[].tags").type(JsonFieldType.ARRAY).description("평가 태그"),
        fieldWithPath("data.reviews[].content").type(JsonFieldType.STRING)
            .description("한 줄 후기 (작성하지 않았으면 빈 문자열)"),
        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
    )
}
