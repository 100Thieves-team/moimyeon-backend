package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType

// FE 상수로 둬도 되는 값이지만 라벨을 서버가 소유한다. 선택지는 enum·상수에서 파생한다 —
// 하드코딩 목이 실 enum 과 어긋났던 사건(MOI-452, FINAL vs ETC)의 재발을 구조적으로 막는다.
data class RoomFormOptionsResponse(
    val rounds: List<CodeLabelResponse>,
    val types: List<CodeLabelResponse>,
    val methods: List<MethodOptionResponse>,
    val durations: List<DurationOptionResponse>,
    val participantConstraints: ParticipantConstraintsResponse,
) {
    companion object {
        private val DURATION_MINUTES = listOf(30, 60, 90, 120)

        fun of(): RoomFormOptionsResponse {
            return RoomFormOptionsResponse(
                rounds = InterviewStage.entries.map { CodeLabelResponse(it.name, it.label) },
                types = InterviewType.entries.map { CodeLabelResponse(it.name, it.label) },
                methods = MeetingType.entries.map { MethodOptionResponse(it.name, it.label, hintOf(it)) },
                durations = DURATION_MINUTES.map { DurationOptionResponse(it, "${it}분") },
                participantConstraints = ParticipantConstraintsResponse(
                    min = RoomCapacity.MIN_PARTICIPANTS,
                    max = RoomCapacity.MAX_PARTICIPANTS,
                ),
            )
        }

        // 폼 전용 안내 문구라 enum 이 아니라 여기서 소유한다. exhaustive when 이라
        // MeetingType 에 값이 추가되면 컴파일 에러로 문구 누락을 알린다.
        private fun hintOf(method: MeetingType): String = when (method) {
            MeetingType.ONLINE -> "화상 링크는 진행이 확정되면 만들어져요."
            MeetingType.OFFLINE -> "지역만 정하면 돼요."
        }
    }
}

data class CodeLabelResponse(
    val code: String,
    val label: String,
)

data class MethodOptionResponse(
    val code: String,
    val label: String,
    val hint: String,
)

data class DurationOptionResponse(
    val minutes: Int,
    val label: String,
)

data class ParticipantConstraintsResponse(
    val min: Int,
    val max: Int,
)
