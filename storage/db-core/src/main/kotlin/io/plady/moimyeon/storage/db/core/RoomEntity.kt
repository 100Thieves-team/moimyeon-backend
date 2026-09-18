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

    fun canStartProgress(at: LocalDateTime): Boolean = status == RoomStatus.CONFIRMED && !at.isBefore(startAt)

    fun startProgress(at: LocalDateTime) {
        check(canStartProgress(at))
        status = RoomStatus.IN_PROGRESS
    }

    // 확정에는 canConfirm() 짝을 두지 않는다. 취소와 진행 시작은 룸이 가진 상태·일정으로 판정하지만,
    // 확정은 참여 인원까지 봐야 하고 그 판정자가 이미 밖에 있다(RoomConfirmation).
    // 여기에 상태 판정을 또 두면 F1 버튼 상태와 서버 결과가 갈릴 수 있다.
    fun confirm() {
        check(status == RoomStatus.RECRUITING)
        status = RoomStatus.CONFIRMED
    }

    // 완료에도 canComplete() 짝을 두지 않는다(confirm 과 같은 이유). 전원 제출 판정은
    // 클로징 제출자·출석자 집합을 아는 호출자(ClosingSubmissionManager)의 몫이다.
    fun complete() {
        check(status == RoomStatus.IN_PROGRESS)
        status = RoomStatus.COMPLETED
    }

    // 나갈 수 있는 상태를 화이트리스트로 둔다. 못 나가는 쪽을 열거하면 상태가 늘 때
    // 기본값이 "나갈 수 있음"이 되어 조용히 열린다.
    fun canLeave(): Boolean = status == RoomStatus.RECRUITING || status == RoomStatus.CONFIRMED

    fun canCancel(): Boolean = status == RoomStatus.RECRUITING

    // 방장이 모집을 접는 것. 운영이 룸을 내리는 deleted_at 과 한 컬럼에 섞지 않는다(schema.sql:450).
    // 참여자 유무는 룸이 알지 못하므로 호출자(RoomManager)가 본다.
    fun cancel() {
        check(canCancel())
        status = RoomStatus.CANCELED
    }

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
