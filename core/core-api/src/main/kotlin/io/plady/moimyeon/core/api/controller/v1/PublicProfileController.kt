package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileActivityResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileActivityRole
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileTagResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileTrustResponse
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

// TODO(MOI-378): 공개 정보는 회원·프로필에서 조회하고, trust 는 이번 스프린트에 null 로 반환한다.
// 신뢰 도메인이 구현되면 목과 같은 응답 모양을 유지한 채 trust 값만 조립한다.
@MockApiProfile
@RestController
class PublicProfileController {
    private val activeMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val withdrawnMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000410")

    @GetMapping("/v1/members/{memberId}/profile")
    fun publicProfile(
        @PathVariable memberId: UUID,
    ): ApiResponse<PublicProfileResponse> {
        val profile = when (memberId) {
            activeMemberId -> activeProfile()
            withdrawnMemberId -> withdrawnProfile()
            else -> throw CoreException(CoreErrorType.MEMBER_NOT_FOUND)
        }
        return ApiResponse.success(profile)
    }

    private fun activeProfile(): PublicProfileResponse {
        return PublicProfileResponse(
            memberId = activeMemberId,
            withdrawn = false,
            nickname = "성실한 사슴 03",
            jobTitle = "백엔드 개발",
            bio = "백엔드 개발자예요. 실전처럼 압박 질문을 주고받는 걸 좋아해요.",
            trust = PublicProfileTrustResponse(
                completedRoomCount = 4,
                attendanceRate = 100,
                noShowCount = 0,
                averageRating = 4.7,
                representativeTags = listOf(
                    PublicProfileTagResponse("시간을 잘 지켜요", 6),
                    PublicProfileTagResponse("질문이 날카로워요", 5),
                    PublicProfileTagResponse("소통이 원활해요", 3),
                ),
            ),
            recentActivities = listOf(
                PublicProfileActivityResponse(
                    PublicProfileActivityRole.PARTICIPANT,
                    "달빛페이 · 백엔드 · 1차 면접",
                    LocalDate.of(2026, 7, 12),
                ),
                PublicProfileActivityResponse(
                    PublicProfileActivityRole.PARTICIPANT,
                    "한빛커머스 · 백엔드 · 2차 면접",
                    LocalDate.of(2026, 6, 28),
                ),
                PublicProfileActivityResponse(
                    PublicProfileActivityRole.HOST,
                    "구름클라우드 · 백엔드 · 최종 면접",
                    LocalDate.of(2026, 6, 14),
                ),
            ),
        )
    }

    private fun withdrawnProfile(): PublicProfileResponse {
        return PublicProfileResponse(
            memberId = withdrawnMemberId,
            withdrawn = true,
            nickname = "탈퇴한 사용자",
            jobTitle = null,
            bio = null,
            trust = null,
            recentActivities = emptyList(),
        )
    }
}
