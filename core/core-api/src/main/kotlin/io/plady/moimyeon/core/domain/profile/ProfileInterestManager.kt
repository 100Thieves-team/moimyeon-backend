package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.AbstractEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyRepository
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

// 관심 회사·관심 직무 조인의 전체 교체. 두 테이블이 구조가 같아 한 곳에서 같은 방식으로 다룬다.
//
// 교체는 지우고 새로 만드는 것이 아니라 차집합만 건드린다 — 그대로 남는 항목은 행을 유지해
// "언제부터 관심이었는가"(created_at)를 보존한다. 값 컬렉션이었다면 한 건만 바뀌어도
// 전량 DELETE 후 재삽입이 일어났을 자리다.
@Component
class ProfileInterestManager(
    private val interestCompanyRepository: MemberProfileInterestCompanyRepository,
    private val interestJobRoleRepository: MemberProfileInterestJobRoleRepository,
) {
    fun replaceAll(memberId: UUID, companyIds: List<Long>, jobRoleIds: List<Long>, now: LocalDateTime) {
        replaceCompanies(memberId, companyIds, now)
        replaceJobRoles(memberId, jobRoleIds, now)
    }

    // 프로필이 소프트 삭제될 때 딸린 관심도 같은 시각으로 함께 가린다.
    fun deleteAll(memberId: UUID, now: LocalDateTime) {
        replaceAll(memberId, emptyList(), emptyList(), now)
    }

    fun findCompanyIds(memberId: UUID): List<Long> {
        return interestCompanyRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.companyId }
    }

    fun findJobRoleIds(memberId: UUID): List<Long> {
        return interestJobRoleRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.jobRoleId }
    }

    private fun replaceCompanies(memberId: UUID, companyIds: List<Long>, now: LocalDateTime) {
        replace(
            repository = interestCompanyRepository,
            existing = interestCompanyRepository.findByMemberIdAndDeletedAtIsNull(memberId),
            refOf = { it.companyId },
            wanted = companyIds,
            create = { MemberProfileInterestCompanyEntity(memberId, it) },
            now = now,
        )
    }

    private fun replaceJobRoles(memberId: UUID, jobRoleIds: List<Long>, now: LocalDateTime) {
        replace(
            repository = interestJobRoleRepository,
            existing = interestJobRoleRepository.findByMemberIdAndDeletedAtIsNull(memberId),
            refOf = { it.jobRoleId },
            wanted = jobRoleIds,
            create = { MemberProfileInterestJobRoleEntity(memberId, it) },
            now = now,
        )
    }

    private fun <T : AbstractEntity> replace(
        repository: JpaRepository<T, Long>,
        existing: List<T>,
        refOf: (T) -> Long,
        wanted: List<Long>,
        create: (Long) -> T,
        now: LocalDateTime,
    ) {
        val wantedRefs = wanted.toSet()
        val existingRefs = existing.map(refOf).toSet()

        existing.filterNot { refOf(it) in wantedRefs }.forEach { it.delete(now) }
        repository.saveAll((wantedRefs - existingRefs).map(create))
    }
}
