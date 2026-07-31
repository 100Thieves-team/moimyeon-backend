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
    fun replaceAll(profileId: UUID, companyIds: List<Long>, jobRoleIds: List<Long>, now: LocalDateTime) {
        replaceCompanies(profileId, companyIds, now)
        replaceJobRoles(profileId, jobRoleIds, now)
    }

    @Transactional
    fun deleteAll(profileId: UUID, now: LocalDateTime) {
        replaceAll(profileId, emptyList(), emptyList(), now)
    }

    private fun replaceCompanies(profileId: UUID, companyIds: List<Long>, now: LocalDateTime) {
        replace(
            repository = interestCompanyRepository,
            existing = interestCompanyRepository.findByProfileIdAndDeletedAtIsNull(profileId),
            refOf = { it.companyId },
            wanted = companyIds,
            create = { MemberProfileInterestCompanyEntity(profileId, it) },
            now = now,
        )
    }

    private fun replaceJobRoles(profileId: UUID, jobRoleIds: List<Long>, now: LocalDateTime) {
        replace(
            repository = interestJobRoleRepository,
            existing = interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(profileId),
            refOf = { it.jobRoleId },
            wanted = jobRoleIds,
            create = { MemberProfileInterestJobRoleEntity(profileId, it) },
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
