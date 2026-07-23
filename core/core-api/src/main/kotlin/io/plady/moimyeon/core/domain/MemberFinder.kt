package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class MemberFinder(
    private val memberRepository: MemberRepository,
) {
    // open-in-view=false 라 매핑(lazy socialAccounts 접근)이 트랜잭션 밖이면 LazyInitializationException.
    // 조회는 읽기 트랜잭션 경계 안에서 도메인으로 변환해 반환한다.
    @Transactional(readOnly = true)
    fun findBySocialAccount(provider: SocialLoginProvider, providerId: String): Member? {
        val entity = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderId(provider, providerId)
        return entity?.let(MemberMapper::toDomain)
    }

    @Transactional(readOnly = true)
    fun findById(memberId: UUID): Member? {
        val entity = memberRepository.findById(memberId).orElse(null)
        return entity?.let(MemberMapper::toDomain)
    }
}
