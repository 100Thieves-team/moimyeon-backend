package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.security.auth.SocialMemberResolver
import org.springframework.stereotype.Component
import java.util.UUID

// security의 SocialMemberResolver 구현체
@Component
class SocialMemberResolverAdapter(
    private val socialAuthService: SocialAuthService,
) : SocialMemberResolver {
    override fun resolve(provider: SocialLoginProvider, providerId: String, email: String?): UUID {
        val verifiedEmail = email ?: throw CoreApiException(CoreApiErrorType.OAUTH_EMAIL_NOT_PROVIDED)
        return socialAuthService.authenticate(provider, providerId, Email(verifiedEmail))
    }
}
