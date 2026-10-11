package io.plady.moimyeon.core.event.growth

import java.util.UUID

// memberId 는 이 사건이 누구의 퍼널에 남는지(주체)다. 속성 값 규칙은 GrowthEventEntry 가 검증한다.
data class GrowthEvent(
    val code: GrowthEventCode,
    val memberId: UUID,
    val properties: Map<String, Any> = emptyMap(),
)
