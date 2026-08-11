package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.WebPushSubscriptionRequest
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.notification.WebPushRegistration
import io.plady.moimyeon.core.domain.notification.WebPushSubscriptionService
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class WebPushSubscriptionControllerTest : RestDocsTest() {
    private val memberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val principal = Principal { memberId.toString() }
    private val service = mockk<WebPushSubscriptionService>()
    private val request = WebPushSubscriptionRequest("fcm-registration-id")

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            WebPushSubscriptionController(service),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `현재 브라우저의 웹 푸시 등록을 저장한다`() {
        justRun { service.register(memberId, WebPushRegistration(request.registration)) }

        mockMvc.perform(
            put(PATH)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "registerWebPushSubscription",
                    "웹 푸시 등록",
                    "인증 회원의 현재 브라우저에서 발급받은 웹 푸시 등록 식별자를 저장한다. " +
                        "같은 값을 다시 보내면 마지막 동기화 시각을 갱신하며, 다른 회원으로 로그인한 브라우저라면 현재 회원에게 이전한다.",
                    requestFields(
                        fieldWithPath("registration").type(JsonFieldType.STRING)
                            .description("FCM 웹 클라이언트가 발급한 등록 식별자"),
                    ),
                    successResponseFields(),
                ),
            )

        verify(exactly = 1) { service.register(memberId, WebPushRegistration(request.registration)) }
    }

    @Test
    fun `현재 회원이 소유한 웹 푸시 등록을 해지한다`() {
        justRun { service.unregister(memberId, WebPushRegistration(request.registration)) }

        mockMvc.perform(
            delete(PATH)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "unregisterWebPushSubscription",
                    "웹 푸시 등록 해지",
                    "현재 회원이 소유한 브라우저 등록을 물리 삭제한다. 이미 없거나 다른 회원이 소유한 등록이면 성공으로 끝나는 멱등 요청이다.",
                    requestFields(
                        fieldWithPath("registration").type(JsonFieldType.STRING)
                            .description("해지할 웹 푸시 등록 식별자"),
                    ),
                    successResponseFields(),
                ),
            )

        verify(exactly = 1) { service.unregister(memberId, WebPushRegistration(request.registration)) }
    }

    private fun successResponseFields() = responseFields(
        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
    )
}

private const val PATH = "/v1/members/me/web-push-subscriptions"
