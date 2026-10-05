package io.plady.moimyeon.core.domain.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class NotificationSettingManager(
    private val memberRepository: MemberRepository,
    private val webPushSubscriptionManager: WebPushSubscriptionManager,
    private val clock: Clock,
) {
    @Transactional
    fun change(
        memberId: UUID,
        change: NotificationSettingChange,
    ) {
        log.debug { "notification-setting.manager.change memberId=$memberId" }
        val member = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        change.isActivityEmailEnabled?.let { member.changeActivityEmail(it) }
        change.isMarketingEmailAgreed?.let { member.changeMarketingEmail(it, LocalDateTime.now(clock)) }
        // 기기 등록 쿼리가 영속성 컨텍스트를 비우므로 회원 변경은 그보다 먼저 한다.
        when (val webPush = change.webPush) {
            is WebPushChange.Allow -> {
                member.allowWebPush()
                webPushSubscriptionManager.register(memberId, webPush.registration)
            }
            WebPushChange.Disallow -> {
                member.disallowWebPush()
                webPushSubscriptionManager.unregisterAll(memberId)
            }
            null -> Unit
        }
    }

    // 끈 뒤에도 다른 기기의 브라우저가 등록을 다시 보내므로 받지 않는다.
    @Transactional
    fun refreshWebPush(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        log.debug { "notification-setting.manager.refreshWebPush memberId=$memberId" }
        val member = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        if (!member.isWebPushAllowed) {
            webPushSubscriptionManager.unregisterIfOwnedByOther(memberId, registration)
            return
        }
        webPushSubscriptionManager.register(memberId, registration)
    }
}
