package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.qa.QaMemberStatus
import java.util.UUID

data class QaMemberStatusResponse(
    val memberId: UUID,
    val before: MemberStatus,
    val status: MemberStatus,
) {
    companion object {
        fun from(result: QaMemberStatus): QaMemberStatusResponse = QaMemberStatusResponse(
            memberId = result.memberId,
            before = result.before,
            status = result.status,
        )
    }
}
