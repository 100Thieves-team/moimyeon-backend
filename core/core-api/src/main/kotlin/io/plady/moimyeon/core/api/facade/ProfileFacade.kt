package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ProfileResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.profile.ProfileContent
import io.plady.moimyeon.core.domain.profile.ProfileService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProfileFacade(
    private val profileService: ProfileService,
    private val catalogService: CatalogService,
) {
    fun update(memberId: UUID, content: ProfileContent): ProfileResponse {
        profileService.update(memberId, content)
        val updated = profileService.getProfile(memberId)
        return ProfileResponse.from(updated, catalogService.getCompanies(updated.interestCompanyIds))
    }
}
