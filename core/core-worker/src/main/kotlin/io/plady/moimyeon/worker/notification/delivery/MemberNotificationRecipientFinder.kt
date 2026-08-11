package io.plady.moimyeon.worker.notification.delivery

import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import io.plady.moimyeon.worker.notification.PermanentNotificationProcessingException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MemberNotificationRecipientFinder(
    private val memberRepository: MemberRepository,
    private val webPushSubscriptionRepository: WebPushSubscriptionRepository,
) : NotificationRecipientFinder {
    override fun find(memberId: UUID): NotificationRecipient {
        val member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            ?: throw NotificationRecipientNotFoundException(memberId)
        return NotificationRecipient(
            email = member.email,
            webPushRegistrations = webPushSubscriptionRepository.findAllByMemberId(memberId)
                .mapTo(linkedSetOf()) { it.registration },
        )
    }
}

class NotificationRecipientNotFoundException(
    memberId: UUID,
) : PermanentNotificationProcessingException("알림 수신 회원을 찾을 수 없습니다. memberId=$memberId")
