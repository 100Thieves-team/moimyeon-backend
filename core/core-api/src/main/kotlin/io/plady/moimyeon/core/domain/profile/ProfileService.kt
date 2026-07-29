package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.domain.catalog.CompanyFinder
import io.plady.moimyeon.core.domain.catalog.JobCatalogFinder
import io.plady.moimyeon.core.domain.catalog.RegionFinder
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.terms.TermsAgreementFinder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProfileService(
    private val memberFinder: MemberFinder,
    private val termsAgreementFinder: TermsAgreementFinder,
    private val jobCatalogFinder: JobCatalogFinder,
    private val regionFinder: RegionFinder,
    private val companyFinder: CompanyFinder,
    private val profileFinder: ProfileFinder,
    private val profileManager: ProfileManager,
) {
    fun create(memberId: UUID, content: ProfileContent): UUID {
        memberFinder.getById(memberId)
        validateCatalogRefs(content)
        requireBusiness(termsAgreementFinder.hasAgreedAllRequiredActive(memberId), CoreErrorType.TERMS_NOT_AGREED)
        requireBusiness(!profileFinder.exists(memberId), CoreErrorType.PROFILE_ALREADY_EXISTS)

        return try {
            profileManager.append(memberId, content)
        } catch (e: DataIntegrityViolationException) {
            // find ~ save 사이의 동시성 방지. 기대하지 않은 무결성 위반을 오인하지 않도록 그 외에는 전파한다.
            if (profileFinder.exists(memberId)) {
                throw CoreException(CoreErrorType.PROFILE_ALREADY_EXISTS)
            }
            throw e
        }
    }

    fun update(memberId: UUID, content: ProfileContent): UUID {
        memberFinder.getById(memberId)
        validateCatalogRefs(content)
        return profileManager.update(memberId, content)
    }

    fun hasProfile(memberId: UUID): Boolean = profileFinder.exists(memberId)

    fun getProfile(memberId: UUID): MemberProfile = profileFinder.getProfile(memberId)

    private fun validateCatalogRefs(content: ProfileContent) {
        content.jobRoleId?.let {
            requireBusiness(jobCatalogFinder.existsActiveRole(it), CoreErrorType.JOB_ROLE_NOT_FOUND)
        }
        content.sigunguId?.let {
            requireBusiness(regionFinder.existsActiveSigungu(it), CoreErrorType.REGION_NOT_FOUND)
        }
        requireBusiness(companyFinder.allActive(content.interestCompanyIds), CoreErrorType.COMPANY_NOT_FOUND)
    }
}
