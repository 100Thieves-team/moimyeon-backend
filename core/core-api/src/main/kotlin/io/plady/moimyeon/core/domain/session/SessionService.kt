package io.plady.moimyeon.core.domain.session

import io.plady.moimyeon.core.domain.member.MemberFinder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SessionService(
    private val sessionManager: SessionManager,
    private val memberFinder: MemberFinder,
) {

    fun refreshAccess(rawCredential: String): UUID {
        val memberId = sessionManager.resolveMemberId(rawCredential)
        memberFinder.getById(memberId)
        return memberId
    }

    fun logout(rawCredential: String) {
        sessionManager.revoke(rawCredential)
    }
}
