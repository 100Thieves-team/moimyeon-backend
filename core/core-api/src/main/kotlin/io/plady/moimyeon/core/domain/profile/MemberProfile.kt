package io.plady.moimyeon.core.domain.profile

import java.util.UUID

data class MemberProfile(
    val memberId: UUID,
    // 가입 시 빈 프로필이 함께 생기므로 "아직 안 채움"이 정상 상태다.
    // 미작성 소개는 null 분기를 만들지 않도록 빈 문자열로 표현한다.
    val bio: String,
    val interestJobRoleIds: List<Long>,
    val interestCompanyIds: List<Long>,
)
