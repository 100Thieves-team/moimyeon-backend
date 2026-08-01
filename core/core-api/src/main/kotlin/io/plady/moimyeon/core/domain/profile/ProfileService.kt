package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.domain.company.CompanyValidator
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProfileService(
    private val catalogRefValidator: CatalogRefValidator,
    private val companyValidator: CompanyValidator,
    private val profileFinder: ProfileFinder,
    private val profileManager: ProfileManager,
) {
    fun update(memberId: UUID, content: ProfileContent): UUID {
        // 선택 가능한 참조인지는 각 소유 개념이 판정하고, 프로필 쓰기 트랜잭션에는 넣지 않는다.
        catalogRefValidator.validateJobRoles(content.interestJobRoleIds)
        content.sigunguId?.let { catalogRefValidator.validateSigungu(it) }
        companyValidator.validateSelectable(content.interestCompanyIds)

        return profileManager.update(memberId, content)
    }

    fun getProfile(memberId: UUID): MemberProfile = profileFinder.getProfile(memberId)
}
