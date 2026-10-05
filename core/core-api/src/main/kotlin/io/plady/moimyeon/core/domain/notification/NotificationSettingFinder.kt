package io.plady.moimyeon.core.domain.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class NotificationSettingFinder(
    private val memberRepository: MemberRepository,
) {
    fun get(memberId: UUID): NotificationSetting {
        log.debug { "notification-setting.finder.get memberId=$memberId" }
        val member = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        return NotificationSetting(
            isWebPushAllowed = member.isWebPushAllowed,
            isActivityEmailEnabled = member.isActivityEmailEnabled,
            isMarketingEmailAgreed = member.isMarketingEmailAgreed,
            marketingEmailAgreedAt = member.marketingEmailAgreedAt,
        )
    }
}
