package io.plady.moimyeon.core.event

import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object EventIdGenerator {
    @OptIn(ExperimentalUuidApi::class)
    fun generate(): UUID = Uuid.generateV7().toJavaUuid()
}
