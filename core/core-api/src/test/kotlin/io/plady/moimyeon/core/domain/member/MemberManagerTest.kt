package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class MemberManagerTest {
    private val memberRepository = mockk<MemberRepository>()
    private val memberManager = MemberManager(memberRepository)

    private val provider = SocialLoginProvider.GOOGLE

    @Test
    fun `append 는 도메인 불변식을 만족한 신규 회원을 저장하고 저장된 id 를 반환한다`() {
        // given
        val saved = slot<MemberEntity>()
        every { memberRepository.save(capture(saved)) } answers { saved.captured }

        // when
        val id = memberManager.append(provider, "sub-1", Email("user@example.com"))

        // then
        val entity = saved.captured
        assertThat(id).isEqualTo(entity.id)
        assertThat(entity.email).isEqualTo("user@example.com")
        assertThat(entity.status).isEqualTo(MemberStatus.ACTIVE) // Member.register 가 ACTIVE 로 생성
        assertThat(entity.withdrawnAt).isNull()
        assertThat(entity.socialAccounts).hasSize(1)
        assertThat(entity.socialAccounts.first().provider).isEqualTo(provider)
        assertThat(entity.socialAccounts.first().providerId).isEqualTo("sub-1")
        assertThat(entity.socialAccounts.first().linkedEmail).isEqualTo("user@example.com")
    }

    @Test
    fun `recordLogin 은 회원의 마지막 로그인 시각을 갱신한다`() {
        // given
        val id = UUID.randomUUID()
        val oldLoginAt = LocalDateTime.of(2020, 1, 1, 0, 0)
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            status = MemberStatus.ACTIVE,
            lastLoginAt = oldLoginAt,
            withdrawnAt = null,
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "user@example.com")),
        )
        every { memberRepository.findById(id) } returns Optional.of(entity)

        // when
        memberManager.recordLogin(id)

        // then
        assertThat(entity.lastLoginAt).isAfter(oldLoginAt)
    }

    @Test
    fun `recordLogin 은 회원이 없으면 MEMBER_NOT_FOUND 예외를 던진다`() {
        // given
        every { memberRepository.findById(any()) } returns Optional.empty()

        // when & then
        assertThatThrownBy { memberManager.recordLogin(UUID.randomUUID()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }
}
