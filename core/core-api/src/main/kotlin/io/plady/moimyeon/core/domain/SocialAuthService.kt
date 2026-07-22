package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SocialAuthService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
) {
    // find ~ save 사이의 동시성(따닥) -> DB 유니크 제약 조건으로 보장
    fun authenticate(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        memberFinder.findBySocialAccount(provider, providerId)?.let {
            memberManager.recordLogin(it.id)
            return it.id
        }

        return memberManager.append(provider, providerId, email)
    }
}
