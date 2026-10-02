package io.plady.moimyeon.core.domain.notification

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.room.activeMember
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationSettingServiceIT(
    private val notificationSettingService: NotificationSettingService,
    private val memberRepository: MemberRepository,
    private val webPushSubscriptionRepository: WebPushSubscriptionRepository,
) : ContextTest() {
    @BeforeEach
    fun setUp() {
        memberRepository.saveAll(listOf(activeMember(MEMBER, "setting-member"), activeMember(OTHER, "setting-other")))
    }

    @AfterEach
    fun tearDown() {
        webPushSubscriptionRepository.deleteAll()
        memberRepository.deleteAllById(listOf(MEMBER, OTHER))
    }

    @Test
    fun `처음 가입한 회원은 웹 푸시를 허용하고 활동 메일은 켜져 있고 광고성 정보에는 동의하지 않은 상태다`() {
        val setting = notificationSettingService.get(MEMBER)

        assertThat(setting.isWebPushAllowed).isTrue()
        assertThat(setting.isActivityEmailEnabled).isTrue()
        assertThat(setting.isMarketingEmailAgreed).isFalse()
        assertThat(setting.marketingEmailAgreedAt).isNull()
    }

    @Test
    fun `보낸 항목만 바뀌고 보내지 않은 항목은 그대로다`() {
        notificationSettingService.change(MEMBER, change(isMarketingEmailAgreed = true))

        val setting = notificationSettingService.change(MEMBER, change(isActivityEmailEnabled = false))

        assertThat(setting.isActivityEmailEnabled).isFalse()
        assertThat(setting.isMarketingEmailAgreed).isTrue()
        assertThat(setting.isWebPushAllowed).isTrue()
    }

    @Test
    fun `광고성 정보에 동의하면 동의 시각을 남기고 다시 동의해도 시각은 그대로다`() {
        val agreed = notificationSettingService.change(MEMBER, change(isMarketingEmailAgreed = true))
        val agreedAgain = notificationSettingService.change(MEMBER, change(isMarketingEmailAgreed = true))

        assertThat(agreed.marketingEmailAgreedAt).isNotNull()
        assertThat(agreedAgain.marketingEmailAgreedAt).isEqualTo(agreed.marketingEmailAgreedAt)
    }

    @Test
    fun `광고성 정보 동의를 철회해도 마지막 동의 시각은 지우지 않는다`() {
        val agreed = notificationSettingService.change(MEMBER, change(isMarketingEmailAgreed = true))

        val withdrawn = notificationSettingService.change(MEMBER, change(isMarketingEmailAgreed = false))

        assertThat(withdrawn.isMarketingEmailAgreed).isFalse()
        assertThat(withdrawn.marketingEmailAgreedAt).isEqualTo(agreed.marketingEmailAgreedAt)
    }

    @Test
    fun `웹 푸시를 허용하면 허용 상태로 바꾸고 지금 쓰는 기기를 등록한다`() {
        notificationSettingService.change(MEMBER, change(webPush = WebPushChange.Disallow))

        val setting = notificationSettingService.change(MEMBER, change(webPush = WebPushChange.Allow(WebPushRegistration("laptop"))))

        assertThat(setting.isWebPushAllowed).isTrue()
        assertThat(registrationsOf(MEMBER)).containsExactly("laptop")
    }

    @Test
    fun `웹 푸시와 메일 항목을 한 번에 바꾸면 모두 반영된다`() {
        val disallowed = notificationSettingService.change(
            MEMBER,
            change(webPush = WebPushChange.Disallow, isActivityEmailEnabled = false, isMarketingEmailAgreed = true),
        )
        val allowed = notificationSettingService.change(
            MEMBER,
            change(webPush = WebPushChange.Allow(WebPushRegistration("laptop")), isActivityEmailEnabled = true, isMarketingEmailAgreed = false),
        )

        assertThat(disallowed.isWebPushAllowed).isFalse()
        assertThat(disallowed.isActivityEmailEnabled).isFalse()
        assertThat(disallowed.isMarketingEmailAgreed).isTrue()
        assertThat(allowed.isWebPushAllowed).isTrue()
        assertThat(allowed.isActivityEmailEnabled).isTrue()
        assertThat(allowed.isMarketingEmailAgreed).isFalse()
        assertThat(registrationsOf(MEMBER)).containsExactly("laptop")
    }

    @Test
    fun `웹 푸시 허용을 해제하면 그 회원의 모든 기기 등록을 지우고 다른 회원의 기기는 남긴다`() {
        notificationSettingService.change(MEMBER, change(webPush = WebPushChange.Allow(WebPushRegistration("laptop"))))
        notificationSettingService.change(MEMBER, change(webPush = WebPushChange.Allow(WebPushRegistration("phone"))))
        notificationSettingService.change(OTHER, change(webPush = WebPushChange.Allow(WebPushRegistration("other-phone"))))

        val setting = notificationSettingService.change(MEMBER, change(webPush = WebPushChange.Disallow))

        assertThat(setting.isWebPushAllowed).isFalse()
        assertThat(registrationsOf(MEMBER)).isEmpty()
        assertThat(registrationsOf(OTHER)).containsExactly("other-phone")
    }

    @Test
    fun `웹 푸시를 허용하지 않은 상태에서는 기기 등록 갱신이 아무것도 남기지 않는다`() {
        notificationSettingService.change(MEMBER, change(webPush = WebPushChange.Disallow))

        notificationSettingService.refreshWebPush(MEMBER, WebPushRegistration("stale-phone"))

        assertThat(registrationsOf(MEMBER)).isEmpty()
        assertThat(notificationSettingService.get(MEMBER).isWebPushAllowed).isFalse()
    }

    @Test
    fun `웹 푸시를 허용하지 않은 회원이 다른 회원의 기기 등록을 보내면 그 등록을 지운다`() {
        notificationSettingService.change(OTHER, change(webPush = WebPushChange.Allow(WebPushRegistration("shared-browser"))))
        notificationSettingService.change(MEMBER, change(webPush = WebPushChange.Disallow))

        notificationSettingService.refreshWebPush(MEMBER, WebPushRegistration("shared-browser"))

        assertThat(registrationsOf(OTHER)).isEmpty()
        assertThat(registrationsOf(MEMBER)).isEmpty()
    }

    @Test
    fun `웹 푸시를 허용한 상태면 기기 등록 갱신이 이 기기를 등록한다`() {
        notificationSettingService.refreshWebPush(MEMBER, WebPushRegistration("phone"))

        assertThat(registrationsOf(MEMBER)).containsExactly("phone")
    }

    @Test
    fun `없는 회원의 설정은 조회하거나 바꿀 수 없다`() {
        val unknown = UUID.fromString("00000000-0000-0000-0000-0000000005ff")

        assertThatThrownBy { notificationSettingService.get(unknown) }
            .isInstanceOfSatisfying(CoreException::class.java) { assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND) }
        assertThatThrownBy { notificationSettingService.change(unknown, change(isActivityEmailEnabled = false)) }
            .isInstanceOfSatisfying(CoreException::class.java) { assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND) }
        assertThatThrownBy { notificationSettingService.refreshWebPush(unknown, WebPushRegistration("phone")) }
            .isInstanceOfSatisfying(CoreException::class.java) { assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND) }
    }

    private fun change(
        webPush: WebPushChange? = null,
        isActivityEmailEnabled: Boolean? = null,
        isMarketingEmailAgreed: Boolean? = null,
    ) = NotificationSettingChange(
        webPush = webPush,
        isActivityEmailEnabled = isActivityEmailEnabled,
        isMarketingEmailAgreed = isMarketingEmailAgreed,
    )

    private fun registrationsOf(memberId: UUID): List<String> = webPushSubscriptionRepository.findAllByMemberId(memberId).map { it.registration }

    private companion object {
        val MEMBER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000501")
        val OTHER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000502")
    }
}
