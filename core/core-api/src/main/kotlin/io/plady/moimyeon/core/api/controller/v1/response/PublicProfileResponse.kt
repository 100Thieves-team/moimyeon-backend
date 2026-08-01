package io.plady.moimyeon.core.api.controller.v1.response

import java.time.LocalDate
import java.util.UUID

data class PublicProfileResponse(
    val memberId: UUID,
    val withdrawn: Boolean,
    val nickname: String,
    val jobTitle: String?,
    val bio: String?,
    val trust: PublicProfileTrustResponse?,
    val recentActivities: List<PublicProfileActivityResponse>,
)

data class PublicProfileTrustResponse(
    val completedRoomCount: Int,
    val attendanceRate: Int,
    val noShowCount: Int,
    val averageRating: Double,
    val representativeTags: List<PublicProfileTagResponse>,
)

data class PublicProfileTagResponse(
    val label: String,
    val count: Int,
)

data class PublicProfileActivityResponse(
    val role: PublicProfileActivityRole,
    val title: String,
    val date: LocalDate,
)

enum class PublicProfileActivityRole {
    PARTICIPANT,
    HOST,
}
