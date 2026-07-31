package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.Company
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.enums.MemberStatus
import java.util.UUID

data class MemberMeResponse(
    val memberId: UUID,
    val email: String,
    val nickname: String,
    val status: MemberStatus,
    val profile: ProfileResponse,
) {
    companion object {
        fun of(member: Member, profile: MemberProfile, interestCompanies: List<Company>): MemberMeResponse {
            return MemberMeResponse(
                memberId = member.id,
                email = member.email.value,
                nickname = member.nickname.value,
                status = member.status,
                profile = ProfileResponse.from(profile, interestCompanies),
            )
        }
    }
}
