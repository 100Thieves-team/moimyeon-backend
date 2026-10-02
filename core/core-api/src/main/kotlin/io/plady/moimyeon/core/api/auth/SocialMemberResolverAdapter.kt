package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.domain.member.SocialAuthentication
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.security.auth.AuthenticatedMember
import io.plady.moimyeon.security.auth.SocialLoginResult
import io.plady.moimyeon.security.auth.SocialMemberResolver
import org.springframework.stereotype.Component

// security의 SocialMemberResolver 구현체
@Component
class SocialMemberResolverAdapter(
    private val socialAuthService: SocialAuthService,
    private val memberFinder: MemberFinder,
) : SocialMemberResolver {
    override fun resolve(provider: SocialLoginProvider, providerId: String, email: String?): SocialLoginResult {
        val verifiedEmail = email ?: throw CoreApiException(CoreApiErrorType.OAUTH_EMAIL_NOT_PROVIDED)
        return when (val authentication = socialAuthService.authenticate(provider, providerId, Email(verifiedEmail))) {
            is SocialAuthentication.LoggedIn -> {
                val member = memberFinder.getById(authentication.memberId)
                SocialLoginResult.Authenticated(AuthenticatedMember(member.id, member.role))
            }
            is SocialAuthentication.Withdrawn -> SocialLoginResult.Withdrawn(authentication.memberId)
        }
    }
}
