package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyRepository
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProfileInterestFinder(
    private val interestCompanyRepository: MemberProfileInterestCompanyRepository,
    private val interestJobRoleRepository: MemberProfileInterestJobRoleRepository,
) {
    fun findCompanyIds(memberId: UUID): List<Long> {
        return interestCompanyRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.companyId }
    }

    fun findJobRoleIds(memberId: UUID): List<Long> {
        return interestJobRoleRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.jobRoleId }
    }
}
