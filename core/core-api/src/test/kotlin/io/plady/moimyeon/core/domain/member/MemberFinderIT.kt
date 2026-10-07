package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MemberFinderIT(
    private val memberFinder: MemberFinder,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @AfterEach
    fun cleanUp() {
        memberRepository.deleteAll()
    }

    @Test
    fun `단건 회원 조회는 Repository 트랜잭션 종료 후에도 소셜 계정을 도메인으로 변환한다`() {
        val memberId = persistMember("google-sub-finder-one", "테스트 회원 01").id

        val found = memberFinder.getById(memberId)

        assertThat(found.socialAccounts.map { it.providerId }).containsExactly("google-sub-finder-one")
    }

    @Test
    fun `다건 회원 조회는 Repository 트랜잭션 종료 후에도 회원별 소셜 계정을 도메인으로 변환한다`() {
        val members = listOf(
            persistMember("google-sub-finder-many-1", "테스트 회원 02"),
            persistMember("google-sub-finder-many-2", "테스트 회원 03"),
        )

        val found = memberFinder.getAllByIds(members.map { it.id })

        assertThat(found.map { it.id }).containsExactlyInAnyOrderElementsOf(members.map { it.id })
        assertThat(found.flatMap { it.socialAccounts }.map { it.providerId })
            .containsExactlyInAnyOrder("google-sub-finder-many-1", "google-sub-finder-many-2")
    }

    private fun persistMember(providerId: String, nickname: String): MemberEntity = memberRepository.saveAndFlush(
        MemberEntity(
            id = UUID.randomUUID(),
            email = "$providerId@example.com",
            nickname = nickname,
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
            socialAccounts = listOf(
                SocialAccountEntity(SocialLoginProvider.GOOGLE, providerId, "$providerId@gmail.com"),
            ),
        ),
    )
}
