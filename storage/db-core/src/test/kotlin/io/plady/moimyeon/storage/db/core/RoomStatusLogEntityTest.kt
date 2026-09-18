package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoomStatusLogHandlerType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 전이 주체 불변식(MEMBER ⇒ 회원 id 있음, SYSTEM ⇒ 없음)은 생성자를 닫고 팩토리 둘로만 지킨다.
// 생성자를 열어 두면 SYSTEM + 아무 id 조합이 컴파일을 통과한다(MOI-471).
class RoomStatusLogEntityTest {
    private val roomId = UUID.randomUUID()
    private val handlerMemberId = UUID.randomUUID()
    private val occurredAt = LocalDateTime.of(2026, 8, 17, 12, 0)

    @Test
    fun `byMember 는 MEMBER 주체와 회원 id 를 기록한다`() {
        val log = RoomStatusLogEntity.byMember(
            roomId = roomId,
            transitionType = RoomStatus.COMPLETED,
            handlerMemberId = handlerMemberId,
            occurredAt = occurredAt,
        )

        assertThat(log.handlerType).isEqualTo(RoomStatusLogHandlerType.MEMBER)
        assertThat(log.handlerMemberId).isEqualTo(handlerMemberId)
        assertThat(log.roomId).isEqualTo(roomId)
        assertThat(log.transitionType).isEqualTo(RoomStatus.COMPLETED)
        assertThat(log.occurredAt).isEqualTo(occurredAt)
    }

    @Test
    fun `bySystem 은 SYSTEM 주체로 기록하고 회원 id 를 두지 않는다`() {
        val log = RoomStatusLogEntity.bySystem(
            roomId = roomId,
            transitionType = RoomStatus.COMPLETED,
            occurredAt = occurredAt,
        )

        assertThat(log.handlerType).isEqualTo(RoomStatusLogHandlerType.SYSTEM)
        assertThat(log.handlerMemberId).isNull()
    }
}
