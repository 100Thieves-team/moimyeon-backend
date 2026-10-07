package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ProfileResponse
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.domain.profile.ProfileService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProfileFacade(
    private val profileService: ProfileService,
    private val companyService: CompanyService,
) {
    fun update(memberId: UUID, nickname: Nickname, content: ProfileContent): ProfileResponse {
        profileService.update(memberId, nickname, content)
        val updated = profileService.getProfile(memberId)
        return ProfileResponse.from(updated, companyService.getCompanies(updated.interestCompanyIds))
    }
}
