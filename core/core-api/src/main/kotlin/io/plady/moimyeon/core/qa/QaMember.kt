package io.plady.moimyeon.core.qa

import java.util.UUID

data class QaMember(
    val id: UUID,
    val nickname: String,
    val email: String,
)
