package io.plady.moimyeon.core.api.controller.v1.response

// 룸 생성 폼(「룸 생성」 §4.1·§4.2)의 선택지 enum 을 한 번에 내려준다.
// FE 상수로 둬도 되는 값이지만, 라벨을 서버가 소유하도록 목으로 제공한다.
data class RoomFormOptionsResponse(
    val rounds: List<CodeLabelResponse>, // 1차/2차/3차/최종
    val types: List<CodeLabelResponse>, // 직무 면접/컬처핏/임원 면접/기술 과제
    val methods: List<MethodOptionResponse>, // 온라인/오프라인
    val durations: List<DurationOptionResponse>, // 예상 시간
    val participantConstraints: ParticipantConstraintsResponse, // 인원 제약
) {
    companion object {
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
