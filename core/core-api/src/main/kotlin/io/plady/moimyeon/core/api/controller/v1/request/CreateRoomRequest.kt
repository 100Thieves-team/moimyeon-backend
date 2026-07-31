package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDate
import java.time.LocalTime

// 룸 생성 위저드(「룸 생성」 §4.1~§4.8)의 입력을 한 번에 받는다.
// 목킹 단계라 형식 수준 검증만 두고, 카탈로그 참조(postingId/jobRoleId/sigunguId)와
// enum 코드(round/type/method)의 실제 유효성·비즈니스 규칙(§4.3 인원, §4.4 일정)은 도메인 구현 시 검증한다.
data class CreateRoomRequest(
    // --- 기본 정보(§4.1) ---
    // 채용 공고: 공고를 고르면 회사가 함께 확정되므로 룸에는 회사를 따로 저장하지 않는다(공고 → 회사 파생).
    // 목록에 없는 공고는 POST /v1/job-postings 로 먼저 만든 뒤 그 id 를 넣는다.
    val postingId: Long,
    val jobRoleId: Long,
    val round: String, // FIRST | SECOND | THIRD | FINAL (면접 차수, 필수)
    val type: String? = null, // JOB | CULTURE_FIT | EXECUTIVE | TECH_ASSIGNMENT (면접 유형, 선택)
    // --- 진행 방식(§4.2) ---
    val method: String, // ONLINE | OFFLINE
    val sigunguId: Long? = null, // OFFLINE 일 때 공개 지역(구/시·군)
    // --- 모집 인원(§4.3) ---
    val minParticipants: Int, // 방장 포함, 2 이상
    val maxParticipants: Int, // 8 이하, min<=max
    // --- 진행 일정(§4.4) ---
    val schedule: RoomScheduleRequest,
    // --- 소개(§4.1) ---
    val title: String,
    val description: String? = null,
    // --- 이력서(§4.5) ---
    // 방장이 제출하는 이력서 = 회원 보관함의 이력서 id(「회원 프로필」 4.4-1, 준서 트랙). 목에서는 참조값으로만 받는다.
    val resumeId: Long,
    // 이력서 원본 공개 여부는 룸 속성이다(§4.5). 방장이 생성 시 정하며 모든 참여자에게 동일 적용된다.
    val resumePublic: Boolean = false,
) {
    // 목킹 단계라 변환할 개념 객체가 아직 없다. 도메인이 붙으면 toXxx() 안으로 옮긴다.
    fun validate() {
        if (minParticipants < MIN_PARTICIPANTS) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        if (maxParticipants > MAX_PARTICIPANTS) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        if (title.isBlank() || title.length > TITLE_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        if (description != null && description.length > DESCRIPTION_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
    }

    companion object {
        private const val MIN_PARTICIPANTS = 2
        private const val MAX_PARTICIPANTS = 8
        private const val TITLE_MAX_LENGTH = 60
        private const val DESCRIPTION_MAX_LENGTH = 1000
    }
}

data class RoomScheduleRequest(
    val date: LocalDate, // 2026-08-01
    val startTime: LocalTime, // 14:00
    val durationMinutes: Int, // 90
)
