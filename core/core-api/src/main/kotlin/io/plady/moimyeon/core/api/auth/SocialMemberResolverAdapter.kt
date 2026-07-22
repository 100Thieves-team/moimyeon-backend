package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.Email
import io.plady.moimyeon.core.domain.SocialAuthService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.ErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.security.auth.SocialMemberResolver
import org.springframework.stereotype.Component
import java.util.UUID


// security의 SocialMemberResolver 구현체
@Component
class SocialMemberResolverAdapter(
    private val socialAuthService: SocialAuthService,
) : SocialMemberResolver {
    override fun resolve(provider: SocialLoginProvider, providerId: String, email: String?): UUID {
        val verifiedEmail = requireFound(email, ErrorType.OAUTH_EMAIL_NOT_PROVIDED)
        return socialAuthService.authenticate(provider, providerId, Email(verifiedEmail))
    }
}
