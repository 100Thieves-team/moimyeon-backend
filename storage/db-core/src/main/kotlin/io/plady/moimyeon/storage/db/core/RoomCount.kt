package io.plady.moimyeon.storage.db.core

import java.util.UUID

// 룸별 집계 한 줄. 탐색 목록이 한 페이지 분량의 roomId 에만 IN 으로 거는 표시용 집계에 쓴다
// (현재 인원, 대기 신청 수). 룸 수에 비례해 쿼리가 늘지 않게 하는 것이 목적이다.
//
// GROUP BY 결과라 행이 하나도 없는 룸은 아예 나오지 않는다. 0 으로 채우는 것은 호출자의 몫이다.
data class RoomCount(
    val roomId: UUID,
    val count: Long,
)
