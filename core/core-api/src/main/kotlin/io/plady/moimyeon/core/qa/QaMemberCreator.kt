package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.MemberRegistrationManager
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaMemberCreator(
    private val memberRegistrationManager: MemberRegistrationManager,
    private val memberFinder: MemberFinder,
) {
    fun create(): QaMember {
        val key = UUID.randomUUID().toString()
        log.debug { "qa-member.creator.create" }
        val memberId = memberRegistrationManager.register(
            provider = SocialLoginProvider.GOOGLE,
            providerId = "$PROVIDER_ID_PREFIX$key",
            email = Email("$EMAIL_LOCAL_PREFIX$key@$EMAIL_DOMAIN"),
        )
        val member = memberFinder.getById(memberId)
        return QaMember(id = member.id, nickname = member.nickname.value, email = member.email.value)
    }

    companion object {
        const val PROVIDER_ID_PREFIX = "qa-"
        const val EMAIL_LOCAL_PREFIX = "qa-"
        const val EMAIL_DOMAIN = "qa.moimyeon.test"
    }
}
