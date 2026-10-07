package io.plady.moimyeon.core.qa

import java.util.UUID

data class QaDataCondition(
    val prefix: String,
    val hostMemberId: UUID?,
    val includeMembers: Boolean = false,
) {
    init {
        require(hasQaMarker(prefix)) { "QA 데이터 접두는 $QA_MARKER 로 시작해야 한다" }
    }

    fun isNarrowed(): Boolean = prefix.length > QA_MARKER.length

    companion object {
        const val QA_MARKER = "[QA]"

        fun hasQaMarker(text: String): Boolean = text.startsWith(QA_MARKER, ignoreCase = true)

        fun isQaData(title: String): Boolean = hasQaMarker(title)
    }
}
