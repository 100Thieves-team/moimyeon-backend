package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "room")
class RoomEntity(
    id: UUID,

    // 식별 참조 — 생성 이후 변경 대상이 아니다(공고를 바꾸면 다른 룸이 된다).
    val jobPostingId: Long,
    val jobRoleId: Long,

    // 이력서 원본 공개 여부는 룸 속성이며, 생성 이후 변경은 별도 스코프다(PRD §4.5).
    val resumePublic: Boolean = false,

    // 아래는 생성 이후 편집 가능한 필드.
    sigunguId: Long?,
    title: String,
    description: String?,
    interviewStage: InterviewStage,
    interviewType: InterviewType?,
    meetingType: MeetingType,
    minCapacity: Short,
    maxCapacity: Short,
    startAt: LocalDateTime,
    durationMinutes: Short,
) : UuidBaseEntity(id) {
    var sigunguId: Long? = sigunguId
        protected set

    var title: String = title
        protected set

    var description: String? = description
        protected set

    @Enumerated(EnumType.STRING)
    var interviewStage: InterviewStage = interviewStage
        protected set

    @Enumerated(EnumType.STRING)
    var interviewType: InterviewType? = interviewType
        protected set

    @Enumerated(EnumType.STRING)
    var meetingType: MeetingType = meetingType
        protected set

    var minCapacity: Short = minCapacity
        protected set

    var maxCapacity: Short = maxCapacity
        protected set

    var startAt: LocalDateTime = startAt
        protected set

    var durationMinutes: Short = durationMinutes
        protected set

    @Enumerated(EnumType.STRING)
    var status: RoomStatus = RoomStatus.RECRUITING
        protected set

    // 편집 가능한 필드 전체 교체. 저장은 변경 감지에 맡긴다(save 호출 없음).
    fun update(
        title: String,
        description: String?,
        interviewStage: InterviewStage,
        interviewType: InterviewType?,
        meetingType: MeetingType,
        sigunguId: Long?,
        minCapacity: Short,
        maxCapacity: Short,
        startAt: LocalDateTime,
        durationMinutes: Short,
    ) {
        this.title = title
        this.description = description
        this.interviewStage = interviewStage
        this.interviewType = interviewType
        this.meetingType = meetingType
        this.sigunguId = sigunguId
        this.minCapacity = minCapacity
        this.maxCapacity = maxCapacity
        this.startAt = startAt
        this.durationMinutes = durationMinutes
    }
}
