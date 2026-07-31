package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SocialLoginProvider
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface MemberRepository : JpaRepository<MemberEntity, UUID> {
    fun findByIdAndDeletedAtIsNull(memberId: UUID): MemberEntity?

    // 쓰기 경로 전용. 회원 행을 잠가 그 회원에 딸린 자식 행 생성을 직렬화한다.
    // 프로필 최초 생성처럼 자식 행이 아직 없어 자식 쪽에는 잠글 대상이 없는 경우,
    // 부모(member) 행 락이 확인-후-저장 레이스를 막는다.
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
