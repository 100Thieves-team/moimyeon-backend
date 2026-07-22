package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.ErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SocialAuthService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
) {
    // find ~ save 사이의 동시성(따닥) -> DB 유니크 제약 조건으로 보장
    fun authenticate(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        memberFinder.findBySocialAccount(provider, providerId)?.let { member ->
            requireBusiness(member.status != MemberStatus.WITHDRAWN, ErrorType.MEMBER_ALREADY_WITHDRAWN)
            memberManager.recordLogin(member.id)
            return member.id
        }

        return memberManager.append(provider, providerId, email)
    }
}
