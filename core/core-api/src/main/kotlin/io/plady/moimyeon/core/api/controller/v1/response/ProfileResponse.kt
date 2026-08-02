package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.enums.MeetingPreference
import java.util.UUID

data class ProfileResponse(
    val memberId: UUID,
    // 직무는 id 만 내려준다 — 표시명은 FE 가 /v1/job-roles 카탈로그에서 해석한다.
    // 관심 회사는 전체 카탈로그를 내려주는 엔드포인트가 없어(검색만 있다) 이름을 함께 담는다.
    val interestJobRoleIds: List<Long>,
    val bio: String,
    val meetingPreference: MeetingPreference,
    val sigunguId: Long?,
    val interestCompanies: List<InterestCompanyResponse>,
) {
    companion object {
        fun from(profile: MemberProfile, interestCompanies: List<Company>): ProfileResponse {
            return ProfileResponse(
                memberId = profile.memberId,
                interestJobRoleIds = profile.interestJobRoleIds,
                bio = profile.bio,
                meetingPreference = profile.meetingPreference,
                sigunguId = profile.sigunguId,
                interestCompanies = interestCompanies.map { InterestCompanyResponse(it.id, it.name) },
            )
        }
    }
}

data class InterestCompanyResponse(
    val companyId: Long,
    val name: String,
)
