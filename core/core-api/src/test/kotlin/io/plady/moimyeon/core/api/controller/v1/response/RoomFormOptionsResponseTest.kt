package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// 폼 선택지가 enum·상수에서 파생되는지 본다. restdocs 는 PR CI 에서 돌지 않으므로
// 목 하드코딩으로의 회귀(FINAL 사건 재발)를 CI 에서 잡는 가드는 이 테스트다.
class RoomFormOptionsResponseTest {
    @Test
    fun `rounds 는 InterviewStage 전체에서 파생된다 - FINAL 은 없고 ETC(기타)가 있다`() {
        val rounds = RoomFormOptionsResponse.of().rounds

        assertThat(rounds.map { it.code }).isEqualTo(InterviewStage.entries.map { it.name })
        assertThat(rounds.map { it.code }).doesNotContain("FINAL")
        assertThat(rounds.last().code).isEqualTo("ETC")
        assertThat(rounds.last().label).isEqualTo("기타")
    }

    @Test
    fun `types 는 InterviewType 전체에서 파생된다`() {
        val types = RoomFormOptionsResponse.of().types

        assertThat(types.map { it.code }).isEqualTo(InterviewType.entries.map { it.name })
        assertThat(types.map { it.label }).isEqualTo(InterviewType.entries.map { it.label })
    }

    @Test
    fun `methods 는 MeetingType 전체를 덮고 각각 안내 문구를 가진다`() {
        val methods = RoomFormOptionsResponse.of().methods

        assertThat(methods.map { it.code }).isEqualTo(MeetingType.entries.map { it.name })
        assertThat(methods).allSatisfy { assertThat(it.hint).isNotBlank() }
    }

    @Test
    fun `durations 는 30 60 90 120 순서다`() {
        val durations = RoomFormOptionsResponse.of().durations

        assertThat(durations.map { it.minutes }).isEqualTo(listOf(30, 60, 90, 120))
        assertThat(durations.map { it.label }).isEqualTo(listOf("30분", "60분", "90분", "120분"))
    }

    @Test
    fun `participantConstraints 는 RoomCapacity 허용 범위와 같다`() {
        val constraints = RoomFormOptionsResponse.of().participantConstraints

        assertThat(constraints.min).isEqualTo(RoomCapacity.MIN_PARTICIPANTS)
        assertThat(constraints.max).isEqualTo(RoomCapacity.MAX_PARTICIPANTS)
    }
}
