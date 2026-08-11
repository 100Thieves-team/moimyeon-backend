package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.security.auth.AuthenticatedMember
import io.plady.moimyeon.security.auth.SocialMemberResolver
import org.springframework.stereotype.Component

// security의 SocialMemberResolver 구현체
@Component
class SocialMemberResolverAdapter(
    private val socialAuthService: SocialAuthService,
    private val memberFinder: MemberFinder,
) : SocialMemberResolver {
    override fun resolve(provider: SocialLoginProvider, providerId: String, email: String?): AuthenticatedMember {
        val verifiedEmail = email ?: throw CoreApiException(CoreApiErrorType.OAUTH_EMAIL_NOT_PROVIDED)
        val memberId = socialAuthService.authenticate(provider, providerId, Email(verifiedEmail))
        val member = memberFinder.getById(memberId)
        return AuthenticatedMember(member.id, member.role)
    }
}
