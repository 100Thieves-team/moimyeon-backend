package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.enums.MeetingPreference
import java.util.UUID

data class MemberProfile(
    val memberId: UUID,
    // 가입 시 빈 프로필이 함께 생기므로 "아직 안 채움"이 정상 상태다.
    // 미지정을 null 로 두면 모든 사용처가 null 분기를 떠안으므로 값으로 표현한다
    // (문자열은 빈 문자열, 만남 선호는 UNSPECIFIED). 카탈로그 참조 id 만 예외로 null 을 쓴다.
    val bio: String,
    val meetingPreference: MeetingPreference,
    val sigunguId: Long?,
    val interestJobRoleIds: List<Long>,
    val interestCompanyIds: List<Long>,
)
