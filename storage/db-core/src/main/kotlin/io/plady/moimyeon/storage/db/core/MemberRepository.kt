package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberRepository : JpaRepository<MemberEntity, UUID> {
    fun findBySocialAccountsProviderAndSocialAccountsProviderId(
        provider: SocialLoginProvider,
        providerId: String,
    ): MemberEntity?
}
