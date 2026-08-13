package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// 활성 룸 한도 규칙 자체(MOI-330 F5). 막는 쪽(RoomManager)과 묻는 쪽(RoomFinder)이 이것을 공유하므로
// 규칙이 맞는지는 여기서만 본다.
class ActiveRoomLimitTest {
    @Test
    fun `모집 중과 확정과 진행 중이 활성이다`() {
        assertThat(ActiveRoomLimit.ACTIVE_STATUSES)
            .containsExactlyInAnyOrder(RoomStatus.RECRUITING, RoomStatus.CONFIRMED, RoomStatus.IN_PROGRESS)
    }

    // 취소는 방장이 접은 것, 완료는 끝난 것 — 둘 다 자리를 돌려준다.
    // 완료 전이는 아직 없지만(MOI-431) 배치가 켜지는 날 코드 변경 없이 풀려야 한다.
    @Test
    fun `취소와 완료는 활성이 아니다`() {
        assertThat(ActiveRoomLimit.ACTIVE_STATUSES)
            .doesNotContain(RoomStatus.CANCELED, RoomStatus.COMPLETED)
    }

    @Test
    fun `활성 룸이 세 개면 한도를 넘는다`() {
        assertThat(ActiveRoomLimit.isExceeded(2)).isFalse()
        assertThat(ActiveRoomLimit.isExceeded(3)).isTrue()
    }

    // 동시 요청으로 한도를 넘긴 상태(MOI-331)에서도 막혀야 한다.
    @Test
    fun `한도를 넘긴 상태도 한도 초과다`() {
        assertThat(ActiveRoomLimit.isExceeded(4)).isTrue()
    }

    @Test
    fun `남은 개수는 한도에서 활성 룸 수를 뺀 값이다`() {
        assertThat(ActiveRoomLimit.remaining(0)).isEqualTo(3)
        assertThat(ActiveRoomLimit.remaining(2)).isEqualTo(1)
        assertThat(ActiveRoomLimit.remaining(3)).isZero()
    }

    // 화면이 이 값으로 일괄 생성 개수를 막는다(PRD 「룸 생성」 §4.4). 음수가 나가면 그 계산이 깨진다.
    @Test
    fun `한도를 넘긴 상태여도 남은 개수는 음수가 되지 않는다`() {
        assertThat(ActiveRoomLimit.remaining(4)).isZero()
    }
}
