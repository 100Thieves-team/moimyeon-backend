package io.plady.moimyeon.core.notification

import io.plady.moimyeon.core.enums.NotificationPolicy
import java.util.UUID

// actionPath 는 프론트 상대 경로다. 환경별 주소는 worker 가 붙인다.
data class ComposedNotification(
    val recipientMemberId: UUID,
    val policy: NotificationPolicy,
    val title: String,
    val body: String,
    val actionPath: String?,
)
