package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.domain.room.RoomCreationCommand
import io.plady.moimyeon.core.domain.room.RoomDescription
import io.plady.moimyeon.core.domain.room.RoomSchedule
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// 룸 생성 위저드(「룸 생성」 §4.1~§4.8)의 입력을 한 번에 받는다.
data class CreateRoomRequest(
    // --- 기본 정보(§4.1) ---
    // 채용 공고: 공고를 고르면 회사가 함께 확정되므로 룸에는 회사를 따로 저장하지 않는다(공고 → 회사 파생).
    // 목록에 없는 공고는 POST /v1/job-postings 로 먼저 만든 뒤 그 id 를 넣는다.
    val postingId: Long,
    val jobRoleId: Long,
    val round: String, // FIRST | SECOND | THIRD | ETC (면접 차수, 필수)
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
    // 방장이 제출하는 이력서 = 회원 보관함의 이력서 id(「회원 프로필」 4.4-1, 준서 트랙). id 체계는 dev와 통일해 UUID.
    val resumeId: UUID,
    // 이력서 원본 공개 여부는 룸 속성이다(§4.5). 방장이 생성 시 정하며 모든 참여자에게 동일 적용된다.
    val resumePublic: Boolean = false,
) {
    // 요청을 도메인 입력으로 변환한다. enum 코드 파싱·진행 방식↔지역 정합은 형식 검증이라 여기서(400) 처리하고,
    // 제목·인원·일정 등 값 규칙은 각 값 객체(RoomTitle/RoomCapacity/RoomSchedule)가 생성 시 검증한다.
    fun toCommand(): RoomCreationCommand = RoomCreationCommand(
        jobPostingId = postingId,
        jobRoleId = jobRoleId,
        title = RoomTitle(title),
        description = description?.let(::RoomDescription),
        interviewStage = parseRoomEnum<InterviewStage>(round),
        interviewType = type?.let { parseRoomEnum<InterviewType>(it) },
        meetingPlace = resolveMeetingPlace(method, sigunguId),
        capacity = RoomCapacity(min = minParticipants, max = maxParticipants),
        schedule = RoomSchedule(
            startAt = schedule.date.atTime(schedule.startTime),
            durationMinutes = schedule.durationMinutes,
        ),
        resumeSharingPolicy =
        if (resumePublic) ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION else ResumeSharingPolicy.AI_SUMMARY_ONLY,
        resumeId = resumeId,
    )
}

data class RoomScheduleRequest(
    val date: LocalDate, // 2026-08-01
    val startTime: LocalTime, // 14:00
    val durationMinutes: Int, // 90
)

// §4.2: 온라인은 지역을 받지 않고, 오프라인은 공개 지역(sigungu)이 필수다. 생성·수정 요청이 공유한다.
internal fun resolveMeetingPlace(method: String, sigunguId: Long?): MeetingPlace = when (parseRoomEnum<MeetingType>(method)) {
    MeetingType.ONLINE -> {
        if (sigunguId != null) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        MeetingPlace.Online
    }

    MeetingType.OFFLINE ->
        MeetingPlace.Offline(
            sigunguId = sigunguId ?: throw CoreApiException(CoreApiErrorType.INVALID_REQUEST),
        )
}

// enum 코드 파싱 실패는 형식 오류(400)다. 도메인 규칙 위반과 구분해 CoreApiException 으로 던진다.
internal inline fun <reified E : Enum<E>> parseRoomEnum(value: String): E = try {
    enumValueOf<E>(value)
} catch (e: IllegalArgumentException) {
    throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
}
