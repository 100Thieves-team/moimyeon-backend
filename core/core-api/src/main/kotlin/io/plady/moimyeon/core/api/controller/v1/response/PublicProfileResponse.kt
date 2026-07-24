package io.plady.moimyeon.core.api.controller.v1.response

import java.util.UUID

data class PublicProfileResponse(
    val memberId: UUID,
    val nickname: String,
    val jobTitle: String?,
    val bio: String?,
    val stats: PublicProfileStatsResponse,
    val frequentReviews: List<FrequentReviewResponse>,
    val recentActivities: List<RecentActivityResponse>,
)

data class PublicProfileStatsResponse(
    val completedInterviewCount: Int,
    val attendanceRate: Int,
    val noShowCount: Int,
    val averageRating: Double,
)

data class FrequentReviewResponse(
    val label: String,
    val count: Int,
)

data class RecentActivityResponse(
    val role: String,
    val title: String,
    val date: String,
)
