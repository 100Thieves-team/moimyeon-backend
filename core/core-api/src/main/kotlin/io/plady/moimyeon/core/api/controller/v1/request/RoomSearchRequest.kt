package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.room.RoomCursor
import io.plady.moimyeon.core.domain.room.RoomSearchCondition
import io.plady.moimyeon.core.domain.room.RoomSortOrder
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDateTime

// 룸 탐색 목록의 쿼리 파라미터(GET /v1/rooms).
//
// 값 규칙은 전부 여기서 확정한다(원칙 2: 검증은 진입점에서 끝낸다). 뒤 레이어에는 스펙 검증이 남지 않는다.
// 태도가 두 갈래인 것은 의도된 정책이다:
//   - 잘못된 필터·정렬 값은 그 값만 무시한다. 사용자가 보는 것은 "조금 넓은 목록"이다(§4.7).
//   - 깨진 커서와 앞뒤가 뒤집힌 조회 범위는 400 이다. 전자는 무시하면 순회가 망가지고,
//     후자는 from 을 버릴지 to 를 버릴지에 따라 결과가 정반대라 "무시"를 정의할 수 없다.
//
// 존재하지 않는 식별자(jobRoleId=99999)는 무시하지 않는다 — 그대로 걸어 결과가 0건이 되게 둔다.
// 조건을 조용히 빼면 화면의 필터 칩과 결과가 어긋나고, 사용자는 필터가 안 걸린 것을 알 수 없다.
data class RoomSearchRequest(
    val companyId: Long? = null,
    val jobPostingId: Long? = null,
    val jobRoleId: Long? = null,
    val round: String? = null,
    val method: String? = null,
    val sigunguId: Long? = null,
    val startFrom: LocalDateTime? = null,
    val startTo: LocalDateTime? = null,
    val availableOnly: Boolean = false,
    val sort: String? = null,
    val cursor: String? = null,
    val size: Int? = null,
) {
    fun toCondition(): RoomSearchCondition {
        if (startFrom != null && startTo != null && startFrom.isAfter(startTo)) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return RoomSearchCondition(
            companyId = companyId,
            jobPostingId = jobPostingId,
            jobRoleId = jobRoleId,
            interviewStage = InterviewStage.entries.firstOrNull { it.name == round },
            meetingType = MeetingType.entries.firstOrNull { it.name == method },
            sigunguId = sigunguId,
            startFrom = startFrom,
            startTo = startTo,
            availableOnly = availableOnly,
        )
    }

    fun toSort(): RoomSortOrder = RoomSortOrder.from(sort)

    fun toCursor(sort: RoomSortOrder): RoomCursor? = cursor?.let { RoomSearchCursorToken.decode(it, sort) }

    // 허용 범위 밖은 기본값으로 떨어뜨린다 — 정렬과 같은 규칙이라 "지원하지 않는 값은 기본값" 한 줄로 설명된다.
    // 상한은 표시용 집계가 IN 으로 붙는 목록 길이를 서버가 스스로 제한하기 위한 값이다.
    fun toSize(): Int = size?.takeIf { it in SIZE_RANGE } ?: DEFAULT_SIZE

    companion object {
        private const val DEFAULT_SIZE = 20
        private val SIZE_RANGE = 1..50
    }
}
