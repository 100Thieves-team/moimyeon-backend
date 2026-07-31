package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyRepository
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 소프트 삭제가 dirty checking 으로 반영되므로, Manager 가 스스로 트랜잭션 경계를 갖지 않으면
// 트랜잭션 없는 호출에서 삭제만 유실되고 삽입만 커밋된다(부분 쓰기). 그 회귀를 막기 위해
// 이 테스트는 의도적으로 클래스 레벨 @Transactional 없이 트랜잭션 밖에서 호출한다.
class ProfileInterestManagerIT(
    val profileInterestManager: ProfileInterestManager,
    val interestCompanyRepository: MemberProfileInterestCompanyRepository,
    val interestJobRoleRepository: MemberProfileInterestJobRoleRepository,
) : ContextTest() {
    private val profileId: UUID = UUID.randomUUID()
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    // 트랜잭션 롤백이 없으므로 이 테스트가 만든 행을 직접 지운다.
    @AfterEach
    fun cleanUp() {
        interestCompanyRepository.deleteAll(interestCompanyRepository.findAll().filter { it.profileId == profileId })
        interestJobRoleRepository.deleteAll(interestJobRoleRepository.findAll().filter { it.profileId == profileId })
    }

    @Test
    fun `트랜잭션 밖에서 replaceAll 을 호출해도 빠진 항목의 소프트 삭제가 반영된다`() {
        // given — 관심 회사 2, 관심 직무 2
        interestCompanyRepository.saveAllAndFlush(
            listOf(101L, 102L).map { MemberProfileInterestCompanyEntity(profileId = profileId, companyId = it) },
        )
        interestJobRoleRepository.saveAllAndFlush(
            listOf(201L, 202L).map { MemberProfileInterestJobRoleEntity(profileId = profileId, jobRoleId = it) },
        )

        // when — 회사 하나를 빼고 직무 하나를 바꾼다. 호출자 트랜잭션 없음.
        profileInterestManager.replaceAll(profileId, listOf(101L), listOf(201L, 203L), now)

        // then — 삭제와 삽입이 함께 반영된다
        assertThat(interestCompanyRepository.findByProfileIdAndDeletedAtIsNull(profileId).map { it.companyId })
            .containsExactly(101L)
        assertThat(interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(profileId).map { it.jobRoleId })
            .containsExactlyInAnyOrder(201L, 203L)

        // 소프트 삭제 — 행 자체는 남는다
        assertThat(interestCompanyRepository.findAll().filter { it.profileId == profileId }).hasSize(2)
        assertThat(interestJobRoleRepository.findAll().filter { it.profileId == profileId }).hasSize(3)
    }

    @Test
    fun `트랜잭션 밖에서 deleteAll 을 호출하면 관심이 전부 소프트 삭제된다`() {
        // given
        interestCompanyRepository.saveAllAndFlush(
            listOf(MemberProfileInterestCompanyEntity(profileId = profileId, companyId = 111L)),
        )
        interestJobRoleRepository.saveAllAndFlush(
            listOf(MemberProfileInterestJobRoleEntity(profileId = profileId, jobRoleId = 211L)),
        )

        // when
        profileInterestManager.deleteAll(profileId, now)

        // then
        assertThat(interestCompanyRepository.findByProfileIdAndDeletedAtIsNull(profileId)).isEmpty()
        assertThat(interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(profileId)).isEmpty()
        assertThat(interestCompanyRepository.findAll().filter { it.profileId == profileId }).hasSize(1)
        assertThat(interestJobRoleRepository.findAll().filter { it.profileId == profileId }).hasSize(1)
    }
}
