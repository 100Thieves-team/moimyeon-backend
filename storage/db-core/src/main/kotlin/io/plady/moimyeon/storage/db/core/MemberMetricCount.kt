package io.plady.moimyeon.storage.db.core

import java.util.UUID

interface MemberMetricCount {
    val memberId: UUID
    val count: Long
}

interface TagMetricCount {
    val label: String
    val count: Long
}
