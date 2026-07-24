package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.response.FrequentReviewResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileStatsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RecentActivityResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// TODO(공개 프로필): 모킹 응답(고정). 신뢰 지표·최근 활동은 세션·피드백 도메인이 생겨야 실 구현 가능하다.
// 마이페이지의 신뢰 지표 카드도 이 API(자신의 memberId)로 조회한다.
@MockApiProfile
@RestController
class PublicProfileController {
    @GetMapping("/v1/members/{memberId}/profile")
    fun publicProfile(
        @PathVariable memberId: UUID,
    ): ApiResponse<PublicProfileResponse> {
        return ApiResponse.success(
            PublicProfileResponse(
                memberId = memberId,
                nickname = "집요한 수달 07",
                jobTitle = "프론트엔드 개발",
                bio = "결제 도메인 3년 차 프론트엔드 개발자예요. 실전처럼 압박 질문을 주고받는 걸 좋아해요.",
                stats = PublicProfileStatsResponse(
                    completedInterviewCount = 12,
                    attendanceRate = 96,
                    noShowCount = 0,
                    averageRating = 4.8,
                ),
                frequentReviews = listOf(
                    FrequentReviewResponse("피드백이 구체적이에요", 8),
                    FrequentReviewResponse("시간을 잘 지켜요", 6),
                    FrequentReviewResponse("질문이 날카로워요", 5),
                ),
                recentActivities = listOf(
                    RecentActivityResponse("PARTICIPANT", "달빛페이 · 프론트엔드 · 1차 면접", "2026-07-12"),
                    RecentActivityResponse("PARTICIPANT", "한빛커머스 · 프론트엔드 · 2차 면접", "2026-06-28"),
                    RecentActivityResponse("HOST", "구름클라우드 · 프론트엔드 · 최종 면접", "2026-06-14"),
                ),
            ),
        )
    }
}
