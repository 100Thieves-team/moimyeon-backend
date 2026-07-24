package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberRepository : JpaRepository<MemberEntity, UUID> {
    fun findByIdAndStatusNot(memberId: UUID, status: MemberStatus): MemberEntity?

    fun findBySocialAccountsProviderAndSocialAccountsProviderIdAndStatusNot(
        provider: SocialLoginProvider,
        providerId: String,
        status: MemberStatus,
    ): MemberEntity?

    fun existsBySocialAccountsProviderAndSocialAccountsProviderIdAndStatusNot(
        provider: SocialLoginProvider,
        providerId: String,
        status: MemberStatus,
    ): Boolean

    fun existsBySocialAccountsProviderAndSocialAccountsProviderIdAndStatus(
        provider: SocialLoginProvider,
        providerId: String,
        status: MemberStatus,
    ): Boolean
}
