package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.domain.trust.TrustService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PublicProfileFacade(
    private val memberService: MemberService,
    private val profileService: ProfileService,
    private val catalogService: CatalogService,
    private val trustService: TrustService,
) {
    fun get(memberId: UUID): PublicProfileResponse {
        val member = memberService.getMember(memberId)
        val profile = profileService.getPublicProfile(memberId)
        val interestJobRoles = catalogService.getJobRoles(profile.interestJobRoleIds)
        val trust = trustService.getPublicTrust(memberId)
        return PublicProfileResponse.of(member, profile, interestJobRoles, trust)
    }
}
