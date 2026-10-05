package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.notification.NotificationSetting
import io.plady.moimyeon.core.domain.notification.NotificationSettingChange
import io.plady.moimyeon.core.domain.notification.NotificationSettingService
import io.plady.moimyeon.core.domain.notification.WebPushChange
import io.plady.moimyeon.core.domain.notification.WebPushRegistration
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

class NotificationSettingControllerTest : RestDocsTest() {
    private val memberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val principal = Principal { memberId.toString() }
    private val notificationSettingService = mockk<NotificationSettingService>()

    private val setting = NotificationSetting(
        isWebPushAllowed = true,
        isActivityEmailEnabled = true,
        isMarketingEmailAgreed = true,
        marketingEmailAgreedAt = LocalDateTime.of(2026, 10, 2, 9, 30),
    )

    private val getSummary = "알림 수신 설정 조회"
    private val getDescription =
        "인증 회원의 알림 수신 설정을 조회한다. isWebPushAllowed 는 회원이 웹 푸시를 허용했는지(끄지 않았는지)를 뜻한다 — " +
            "실제로 이 브라우저에서 받는지는 브라우저 알림 권한과 이 브라우저의 등록 여부를 함께 보고 화면이 정한다. " +
            "인증 정보 없음·무효 401(E1102)로 응답한다."
    private val updateSummary = "알림 수신 설정 변경"
    private val updateDescription =
        "보낸 항목만 바꾸고 보내지 않은 항목은 그대로 둔다. " +
            "isWebPushAllowed=true 는 이 브라우저의 webPushRegistration 과 함께 보내야 하며, 허용으로 바꾸고 이 브라우저를 등록한다. " +
            "isWebPushAllowed=false 는 회원이 등록한 모든 브라우저의 등록을 지운다. " +
            "isMarketingEmailAgreed 를 동의로 바꾸면 그 시각을 marketingEmailAgreedAt 에 남기고, 철회해도 그 시각은 지우지 않는다. " +
            "바꿀 항목이 없거나 isWebPushAllowed·webPushRegistration 짝이 맞지 않으면 400(E400), 등록 식별자가 비어 있으면 400(E1601), " +
            "인증 정보 없음·무효 401(E1102)로 응답한다."
    private val refreshSummary = "웹 푸시 등록 갱신"
    private val refreshDescription =
        "알림 권한을 받은 브라우저가 앱을 열 때 자신의 등록 식별자를 다시 보낸다. 회원이 웹 푸시를 허용하지 않은 상태면 저장하지 않고 성공으로 끝난다 — " +
            "다른 기기에서 끈 뒤 남아 있는 브라우저가 등록을 되살리지 못하게 하기 위해서다. " +
            "이때 그 등록이 다른 회원 것이면 지운다 — 같은 브라우저를 쓰던 앞사람의 알림이 계속 뜨지 않게 한다. " +
            "같은 값을 다시 보내면 마지막 동기화 시각을 갱신하며, 다른 회원으로 로그인한 브라우저라면 현재 회원에게 이전한다. " +
            "등록 식별자가 비어 있으면 400(E1601), 인증 정보 없음·무효 401(E1102)로 응답한다."

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            NotificationSettingController(notificationSettingService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun getNotificationSetting() {
        every { notificationSettingService.get(memberId) } returns setting

        mockMvc.perform(get(SETTING_PATH).principal(principal))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"isWebPushAllowed\":true") }
            .andDo(documentApi("getNotificationSetting", getSummary, getDescription, settingResponseFields()))
    }

    @Test
    fun `getNotificationSetting 인증 없음 E1102`() {
        mockMvc.perform(get(SETTING_PATH))
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("getNotificationSetting-e1102", getSummary, getDescription, errorResponseFields()))
    }

    @Test
    fun updateNotificationSetting() {
        val change = NotificationSettingChange(webPush = null, isActivityEmailEnabled = false, isMarketingEmailAgreed = true)
        every { notificationSettingService.change(memberId, change) } returns setting.copy(isActivityEmailEnabled = false)

        performUpdate("""{"isActivityEmailEnabled":false,"isMarketingEmailAgreed":true}""")
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"isActivityEmailEnabled\":false") }
            .andDo(
                documentApi(
                    "updateNotificationSetting",
                    updateSummary,
                    updateDescription,
                    requestFields(
                        fieldWithPath("isWebPushAllowed").type(JsonFieldType.BOOLEAN).optional()
                            .description("웹 푸시 허용. true 면 webPushRegistration 필수, false 면 모든 브라우저 등록 삭제"),
                        fieldWithPath("webPushRegistration").type(JsonFieldType.STRING).optional()
                            .description("이 브라우저의 FCM 등록 식별자. isWebPushAllowed=true 일 때만 보낸다"),
                        fieldWithPath("isActivityEmailEnabled").type(JsonFieldType.BOOLEAN).optional()
                            .description("서비스 활동 알림 메일 수신"),
                        fieldWithPath("isMarketingEmailAgreed").type(JsonFieldType.BOOLEAN).optional()
                            .description("광고성 정보 메일 수신 동의"),
                    ),
                    settingResponseFields(),
                ),
            )
    }

    @Test
    fun `웹 푸시를 허용하면 이 브라우저 등록과 함께 바꾼다`() {
        val change = NotificationSettingChange(
            webPush = WebPushChange.Allow(WebPushRegistration("fcm-registration-id")),
            isActivityEmailEnabled = null,
            isMarketingEmailAgreed = null,
        )
        every { notificationSettingService.change(memberId, change) } returns setting

        performUpdate("""{"isWebPushAllowed":true,"webPushRegistration":"fcm-registration-id"}""")
            .andExpect(status().isOk)

        verify(exactly = 1) { notificationSettingService.change(memberId, change) }
    }

    @Test
    fun `웹 푸시 허용을 해제하면 등록 식별자 없이 바꾼다`() {
        val change = NotificationSettingChange(webPush = WebPushChange.Disallow, isActivityEmailEnabled = null, isMarketingEmailAgreed = null)
        every { notificationSettingService.change(memberId, change) } returns setting.copy(isWebPushAllowed = false)

        performUpdate("""{"isWebPushAllowed":false}""")
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"isWebPushAllowed\":false") }

        verify(exactly = 1) { notificationSettingService.change(memberId, change) }
    }

    @Test
    fun `updateNotificationSetting 바꿀 항목 없음 E400`() {
        performUpdate("{}")
            .andExpect(status().isBadRequest)
            .andDo(documentApi("updateNotificationSetting-e400", updateSummary, updateDescription, errorResponseFields()))
    }

    @Test
    fun `웹 푸시를 허용하면서 등록 식별자를 보내지 않으면 E400`() {
        performUpdate("""{"isWebPushAllowed":true}""")
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
    }

    @Test
    fun `웹 푸시를 허용하지 않으면서 등록 식별자만 보내면 E400`() {
        performUpdate("""{"isActivityEmailEnabled":true,"webPushRegistration":"fcm-registration-id"}""")
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
    }

    @Test
    fun `updateNotificationSetting 등록 식별자 공백 E1601`() {
        performUpdate("""{"isWebPushAllowed":true,"webPushRegistration":" "}""")
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1601\"") }
            .andDo(documentApi("updateNotificationSetting-e1601", updateSummary, updateDescription, errorResponseFields()))
    }

    @Test
    fun refreshWebPushSubscription() {
        justRun { notificationSettingService.refreshWebPush(memberId, WebPushRegistration("fcm-registration-id")) }

        performRefresh("""{"registration":"fcm-registration-id"}""")
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "refreshWebPushSubscription",
                    refreshSummary,
                    refreshDescription,
                    requestFields(
                        fieldWithPath("registration").type(JsonFieldType.STRING)
                            .description("FCM 웹 클라이언트가 발급한 등록 식별자"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )

        verify(exactly = 1) { notificationSettingService.refreshWebPush(memberId, WebPushRegistration("fcm-registration-id")) }
    }

    @Test
    fun `updateNotificationSetting 인증 없음 E1102`() {
        mockMvc.perform(
            patch(SETTING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"isActivityEmailEnabled":false}"""),
        )
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("updateNotificationSetting-e1102", updateSummary, updateDescription, errorResponseFields()))
    }

    @Test
    fun `refreshWebPushSubscription 인증 없음 E1102`() {
        mockMvc.perform(
            put(SUBSCRIPTION_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"registration":"fcm-registration-id"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("refreshWebPushSubscription-e1102", refreshSummary, refreshDescription, errorResponseFields()))
    }

    @Test
    fun `refreshWebPushSubscription 등록 식별자 공백 E1601`() {
        performRefresh("""{"registration":" "}""")
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1601\"") }
            .andDo(documentApi("refreshWebPushSubscription-e1601", refreshSummary, refreshDescription, errorResponseFields()))
    }

    private fun performUpdate(body: String) = mockMvc.perform(
        patch(SETTING_PATH)
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun performRefresh(body: String) = mockMvc.perform(
        put(SUBSCRIPTION_PATH)
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun settingResponseFields() = responseFields(
        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
        fieldWithPath("data.isWebPushAllowed").type(JsonFieldType.BOOLEAN).description("회원이 웹 푸시를 허용했는지(끄지 않았는지)"),
        fieldWithPath("data.isActivityEmailEnabled").type(JsonFieldType.BOOLEAN).description("서비스 활동 알림 메일 수신"),
        fieldWithPath("data.isMarketingEmailAgreed").type(JsonFieldType.BOOLEAN).description("광고성 정보 메일 수신 동의"),
        fieldWithPath("data.marketingEmailAgreedAt").type(JsonFieldType.STRING).optional()
            .description("광고성 정보 수신 마지막 동의 시각. 동의한 적 없으면 null"),
        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
    )
}

private const val SETTING_PATH = "/v1/members/me/notification-setting"
private const val SUBSCRIPTION_PATH = "/v1/members/me/web-push-subscriptions"
