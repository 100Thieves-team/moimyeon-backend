package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.MemberMeResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.profile.ProfileService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MemberFacade(
    private val memberService: MemberService,
    private val profileService: ProfileService,
    private val catalogService: CatalogService,
) {
    fun me(memberId: UUID): MemberMeResponse {
        val member = memberService.getMember(memberId)
        val profile = if (profileService.hasProfile(memberId)) profileService.getProfile(memberId) else null
        val interestCompanies = profile?.let { catalogService.getCompanies(it.interestCompanyIds) }.orEmpty()
        return MemberMeResponse.of(member, profile, interestCompanies)
    }
}
