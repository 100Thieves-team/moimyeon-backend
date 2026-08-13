package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomStatus
import java.util.UUID

// 생성 요청의 결과. 새로 만든 룸일 수도, 중복 요청이라 돌려준 기존 룸일 수도 있다(MOI-331).
//
// status 를 함께 내리는 이유: 기존 룸은 그 사이 확정됐을 수 있어 RECRUITING 이 아닐 수 있다.
// 만들었는지 돌려준 것인지는 구분해 내리지 않는다 — 화면이 할 일이 같고, 응답도 같은 200 이다.
data class RoomCreationResult(
    val roomId: UUID,
    val status: RoomStatus,
)
