package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.domain.company.CompanyValidator
import io.plady.moimyeon.core.domain.member.Nickname
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProfileService(
    private val catalogRefValidator: CatalogRefValidator,
    private val companyValidator: CompanyValidator,
    private val profileFinder: ProfileFinder,
    private val profileUpdater: ProfileUpdater,
) {
    fun update(memberId: UUID, nickname: Nickname, content: ProfileContent): UUID {
        // 선택 가능 여부는 요청을 받아들이는 시점의 조건이지 프로필에 영구히 유지할 불변식이 아니다.
        // 회사는 저장 후에도 검증 해제·폐기될 수 있고 기존 관심 참조는 이력으로 남으므로,
        // 각 소유 개념이 쓰기 트랜잭션 밖에서 현재 상태만 판정한다.
        catalogRefValidator.validateJobRoles(content.interestJobRoleIds)
        companyValidator.validateSelectable(content.interestCompanyIds)

        return profileUpdater.update(memberId, nickname, content)
    }

    fun getProfile(memberId: UUID): MemberProfile = profileFinder.getProfile(memberId)

    fun getPublicProfile(memberId: UUID): MemberProfile = profileFinder.getPublicProfile(memberId)

    fun getProfiles(memberIds: Collection<UUID>): List<MemberProfile> = profileFinder.getAllByMemberIds(memberIds)
}
