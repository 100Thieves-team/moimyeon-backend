package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.enums.AttendanceStatus

data class PublicTrust(
    val activityTopPercent: Int?,
    val recentAttendances: List<AttendanceStatus>,
    val noShowCount: Int,
    val representativeTags: List<RepresentativeTag>,
) {
    companion object {
        fun empty(): PublicTrust = PublicTrust(
            activityTopPercent = null,
            recentAttendances = emptyList(),
            noShowCount = 0,
            representativeTags = emptyList(),
        )
    }
}

data class RepresentativeTag(
    val label: String,
    val count: Int,
)
