package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberRepository : JpaRepository<MemberEntity, UUID> {
    fun findByIdAndStatusNot(memberId: UUID, status: MemberStatus): MemberEntity?

    // 닉네임 유일성은 상태와 무관하게 전체 회원 대상이다(유니크 제약과 동일 기준)
    fun existsByNickname(nickname: String): Boolean

    fun existsByNicknameAndIdNot(nickname: String, id: UUID): Boolean

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
