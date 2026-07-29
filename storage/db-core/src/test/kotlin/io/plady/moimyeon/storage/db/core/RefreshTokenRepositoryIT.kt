package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RefreshTokenRepositoryIT(
    val refreshTokenRepository: RefreshTokenRepository,
    val memberRepository: MemberRepository,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    // refresh_token.member_id 가 실제 회원을 가리키도록 회원을 먼저 저장한다(FK 는 없지만 데이터 정합 유지).
    private fun persistMember(providerId: String = "google-sub-1"): UUID {
        val member = MemberEntity(
            id = UUID.randomUUID(),
            email = "user@example.com",
            nickname = "nick-$providerId",
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
            withdrawnAt = null,
            socialAccounts = mutableListOf(
                SocialAccountEntity(SocialLoginProvider.GOOGLE, providerId, "user@example.com"),
            ),
        )
        return memberRepository.saveAndFlush(member).id
    }

    @Test
    fun `리프레시 토큰을 저장하고 해시로 조회한다`() {
        // given
        val memberId = persistMember()
        refreshTokenRepository.saveAndFlush(
            RefreshTokenEntity(tokenHash = "hash-1", memberId = memberId, expiresAt = now.plusDays(14)),
        )

        // when
        val found = refreshTokenRepository.findByTokenHash("hash-1")

        // then
        assertThat(found).isNotNull
        assertThat(found!!.memberId).isEqualTo(memberId)
        assertThat(found.revokedAt).isNull()
    }

    @Test
    fun `같은 token_hash 는 유니크 제약으로 중복 저장되지 않는다`() {
        // given
        val memberId = persistMember()
        refreshTokenRepository.saveAndFlush(RefreshTokenEntity("dup-hash", memberId, now.plusDays(14)))

        // when & then
        assertThatThrownBy {
            refreshTokenRepository.saveAndFlush(RefreshTokenEntity("dup-hash", memberId, now.plusDays(14)))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `만료 시각 이전 토큰만 삭제한다`() {
        // given
        val memberId = persistMember()
        refreshTokenRepository.saveAndFlush(RefreshTokenEntity("expired", memberId, now.minusDays(1)))
        refreshTokenRepository.saveAndFlush(RefreshTokenEntity("valid", memberId, now.plusDays(14)))

        // when
        val deleted = refreshTokenRepository.deleteByExpiresAtBefore(now)

        // then
        assertThat(deleted).isEqualTo(1)
        assertThat(refreshTokenRepository.findByTokenHash("expired")).isNull()
        assertThat(refreshTokenRepository.findByTokenHash("valid")).isNotNull
    }
}
