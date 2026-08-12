package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MemberRepository : JpaRepository<MemberEntity, UUID> {
    fun findByIdAndDeletedAtIsNull(memberId: UUID): MemberEntity?

    fun findByIdInAndDeletedAtIsNull(memberIds: Collection<UUID>): List<MemberEntity>

    @Query(
        """
        select distinct m
        from MemberEntity m
        left join fetch m.socialAccounts
        where m.id = :memberId
          and m.deletedAt is null
        """,
    )
    fun findWithSocialAccountsByIdAndDeletedAtIsNull(
        @Param("memberId") memberId: UUID,
    ): MemberEntity?

    @Query(
        """
        select distinct m
        from MemberEntity m
        left join fetch m.socialAccounts
        where m.id in :memberIds
          and m.deletedAt is null
        """,
    )
    fun findAllWithSocialAccountsByIdInAndDeletedAtIsNull(
        @Param("memberIds") memberIds: Collection<UUID>,
    ): List<MemberEntity>

    fun existsByIdAndDeletedAtIsNull(memberId: UUID): Boolean

    // 방장 자동 위임의 자격 판정(MOI-397). 막는 게 아니라 건너뛰는 것이라 예외를 던지지 않는다.
    fun existsByIdAndStatusAndDeletedAtIsNull(memberId: UUID, status: MemberStatus): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByIdAndDeletedAtIsNull(memberId: UUID): MemberEntity?

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
