package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType

// 룸 생성 폼(「룸 생성」 §4.1·§4.2)의 선택지를 한 번에 내려준다.
// FE 상수로 둬도 되는 값이지만 라벨을 서버가 소유한다. 선택지는 enum·상수에서 파생한다 —
// 하드코딩 목이 실 enum 과 어긋났던 사건(MOI-452, FINAL vs ETC)의 재발을 구조적으로 막는다.
data class RoomFormOptionsResponse(
    val rounds: List<CodeLabelResponse>, // 1차/2차/3차/기타
    val types: List<CodeLabelResponse>, // 직무 면접/컬쳐핏 면접/임원 면접/기술 과제
    val methods: List<MethodOptionResponse>, // 온라인/오프라인
    val durations: List<DurationOptionResponse>, // 예상 시간
    val participantConstraints: ParticipantConstraintsResponse, // 인원 제약
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

        fun mock(): RoomFormOptionsResponse {
            return RoomFormOptionsResponse(
                rounds = listOf(
                    CodeLabelResponse("FIRST", "1차"),
                    CodeLabelResponse("SECOND", "2차"),
                    CodeLabelResponse("THIRD", "3차"),
                    CodeLabelResponse("FINAL", "최종"),
                ),
                types = listOf(
                    CodeLabelResponse("JOB", "직무 면접"),
                    CodeLabelResponse("CULTURE_FIT", "컬처핏"),
                    CodeLabelResponse("EXECUTIVE", "임원 면접"),
                    CodeLabelResponse("TECH_ASSIGNMENT", "기술 과제"),
                ),
                methods = listOf(
                    MethodOptionResponse("ONLINE", "온라인", "화상 링크는 진행이 확정되면 만들어져요."),
                    MethodOptionResponse("OFFLINE", "오프라인", "지역만 정하면 돼요."),
                ),
                durations = listOf(
                    DurationOptionResponse(30, "30분"),
                    DurationOptionResponse(60, "60분"),
                    DurationOptionResponse(90, "90분"),
                    DurationOptionResponse(120, "120분"),
                ),
                participantConstraints = ParticipantConstraintsResponse(min = 2, max = 8),
            )
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
