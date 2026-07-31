package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberRepository : JpaRepository<MemberEntity, UUID> {
    fun findByIdAndDeletedAtIsNull(memberId: UUID): MemberEntity?

    // 닉네임 유일성은 삭제 여부와 무관하게 전체 회원 대상이다(유니크 제약과 동일 기준)
    fun existsByNickname(nickname: String): Boolean

    fun existsByNicknameAndIdNot(nickname: String, id: UUID): Boolean

    fun findBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
        provider: SocialLoginProvider,
        providerId: String,
    ): MemberEntity?

    fun existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
        provider: SocialLoginProvider,
        providerId: String,
    ): Boolean

    // 탈퇴자 재가입 차단용. 소프트 삭제된 회원의 소셜 계정은 남아 있으므로 그대로 조회된다.
    fun existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNotNull(
        provider: SocialLoginProvider,
        providerId: String,
    ): Boolean
}
