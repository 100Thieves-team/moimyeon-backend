package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProfileService(
    private val catalogRefValidator: CatalogRefValidator,
    private val profileFinder: ProfileFinder,
    private val profileManager: ProfileManager,
) {
    fun update(memberId: UUID, content: ProfileContent): UUID {
        // 카탈로그 참조는 세 테이블을 조회해야 하고 쓰기 트랜잭션에 넣을 이유가 없어 도구로 분리한다.
        catalogRefValidator.validateJobRoles(content.interestJobRoleIds)
        content.sigunguId?.let { catalogRefValidator.validateSigungu(it) }
        catalogRefValidator.validateCompanies(content.interestCompanyIds)

        return profileManager.update(memberId, content)
    }

    fun getProfile(memberId: UUID): MemberProfile = profileFinder.getProfile(memberId)
}
