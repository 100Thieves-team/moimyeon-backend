package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.support.error.ErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SessionService(
    private val sessionManager: SessionManager,
    private val memberFinder: MemberFinder,
) {

    fun refreshAccess(rawCredential: String): UUID {
        val memberId = sessionManager.resolveMemberId(rawCredential)
        val member = requireFound(memberFinder.findById(memberId), ErrorType.MEMBER_NOT_FOUND)
        requireBusiness(member.status != MemberStatus.WITHDRAWN, ErrorType.MEMBER_ALREADY_WITHDRAWN)
        return memberId
    }

    fun logout(rawCredential: String) {
        sessionManager.revoke(rawCredential)
    }
}
