package io.plady.moimyeon.core.notification

import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object NotificationEventIdGenerator {
    @OptIn(ExperimentalUuidApi::class)
    fun generate(): UUID = Uuid.generateV7().toJavaUuid()
}
