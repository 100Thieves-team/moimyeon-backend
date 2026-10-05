package io.plady.moimyeon.core.domain.notification

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class NotificationSettingServiceTest {
    private val finder = mockk<NotificationSettingFinder>()
    private val manager = mockk<NotificationSettingManager>()
    private val notificationSettingService = NotificationSettingService(finder, manager)

    @Test
    fun `수신 설정 조회는 Finder 결과를 그대로 반환한다`() {
        every { finder.get(MEMBER) } returns SETTING

        val result = notificationSettingService.get(MEMBER)

        assertThat(result).isEqualTo(SETTING)
    }

    @Test
    fun `수신 설정 변경은 Manager 로 바꾼 뒤 다시 조회한 설정을 반환한다`() {
        justRun { manager.change(MEMBER, CHANGE) }
        every { finder.get(MEMBER) } returns SETTING

        val result = notificationSettingService.change(MEMBER, CHANGE)

        assertThat(result).isEqualTo(SETTING)
        verifyOrder {
            manager.change(MEMBER, CHANGE)
            finder.get(MEMBER)
        }
    }

    @Test
    fun `수신 설정 변경이 실패하면 다시 조회하지 않고 예외를 전파한다`() {
        every { manager.change(MEMBER, CHANGE) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        assertThatThrownBy { notificationSettingService.change(MEMBER, CHANGE) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
        verify(exactly = 0) { finder.get(any()) }
    }

    @Test
    fun `기기 등록 갱신은 Manager 에 위임한다`() {
        val registration = WebPushRegistration("fcm-registration-id")
        justRun { manager.refreshWebPush(MEMBER, registration) }

        notificationSettingService.refreshWebPush(MEMBER, registration)

        verify(exactly = 1) { manager.refreshWebPush(MEMBER, registration) }
    }

    @Test
    fun `기기 등록 갱신이 실패하면 예외를 전파한다`() {
        val registration = WebPushRegistration("fcm-registration-id")
        every { manager.refreshWebPush(MEMBER, registration) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        assertThatThrownBy { notificationSettingService.refreshWebPush(MEMBER, registration) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    private companion object {
        val MEMBER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val SETTING = NotificationSetting(
            isWebPushAllowed = true,
            isActivityEmailEnabled = true,
            isMarketingEmailAgreed = true,
            marketingEmailAgreedAt = LocalDateTime.of(2026, 10, 2, 9, 30),
        )
        val CHANGE = NotificationSettingChange(
            webPush = WebPushChange.Disallow,
            isActivityEmailEnabled = false,
            isMarketingEmailAgreed = null,
        )
    }
}
