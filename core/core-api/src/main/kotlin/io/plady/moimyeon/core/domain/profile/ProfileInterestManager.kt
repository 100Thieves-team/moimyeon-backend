package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.AbstractEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyRepository
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class ProfileInterestManager(
    private val interestCompanyRepository: MemberProfileInterestCompanyRepository,
    private val interestJobRoleRepository: MemberProfileInterestJobRoleRepository,
) {
    @Transactional
    fun replaceAll(memberId: UUID, companyIds: List<Long>, jobRoleIds: List<Long>, now: LocalDateTime) {
        replaceCompanies(memberId, companyIds, now)
        replaceJobRoles(memberId, jobRoleIds, now)
    }

    @Transactional
    fun deleteAll(memberId: UUID, now: LocalDateTime) {
        replaceAll(memberId, emptyList(), emptyList(), now)
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
