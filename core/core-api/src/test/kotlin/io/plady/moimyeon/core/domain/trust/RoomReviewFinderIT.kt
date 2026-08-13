package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomReviewFinderIT(
    private val roomReviewFinder: RoomReviewFinder,
    private val roomRepository: RoomRepository,
    private val attendanceRepository: AttendanceRepository,
    private val reviewRepository: ReviewRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000436")
    private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000437")
    private val recorderMemberId = UUID.fromString("00000000-0000-0000-0000-000000000438")

    @Test
    fun `완료 룸의 참석 기록과 작성한 후기로 후기 상태를 구분한다`() {
        val writableRoomId = persistCompletedRoom("후기 작성할 백엔드 모의면접")
        persistAttendance(writableRoomId, memberId, AttendanceStatus.ATTENDED)
        persistAttendance(writableRoomId, otherMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(writableRoomId, recorderMemberId, AttendanceStatus.ATTENDED)
        persistReview(writableRoomId, otherMemberId)

        val writtenRoomId = persistCompletedRoom("후기 작성한 백엔드 모의면접")
        persistAttendance(writtenRoomId, memberId, AttendanceStatus.ATTENDED)
        persistAttendance(writtenRoomId, otherMemberId, AttendanceStatus.ATTENDED)
        persistReview(writtenRoomId, otherMemberId)

        val absentRoomId = persistCompletedRoom("불참한 백엔드 모의면접")
        persistAttendance(absentRoomId, memberId, AttendanceStatus.ABSENT)
        persistAttendance(absentRoomId, otherMemberId, AttendanceStatus.ATTENDED)

        val noTargetRoomId = persistCompletedRoom("후기 대상 없는 백엔드 모의면접")
        persistAttendance(noTargetRoomId, memberId, AttendanceStatus.ATTENDED)

        val result = roomReviewFinder.getSummaries(
            memberId,
            listOf(writableRoomId, writtenRoomId, absentRoomId, noTargetRoomId),
        )

        assertThat(result.values.map { it.attendedParticipantCount }).containsExactly(3, 2, 1, 1)
        assertThat(result.values.map { it.status })
            .containsExactly(
                RoomReviewStatus.WRITABLE,
                RoomReviewStatus.WRITTEN,
                RoomReviewStatus.NOT_ELIGIBLE_ABSENT,
                RoomReviewStatus.NOT_ELIGIBLE_NO_TARGET,
            )
    }

    private fun persistCompletedRoom(title: String): UUID {
        val roomId = UUID.randomUUID()
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = title,
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 5,
                startAt = LocalDateTime.of(2026, 8, 10, 19, 0),
                durationMinutes = 60,
            ),
        )
        entityManager.createNativeQuery("update room set status = :status where id = :roomId")
            .setParameter("status", RoomStatus.COMPLETED.name)
            .setParameter("roomId", roomId)
            .executeUpdate()
        entityManager.clear()
        return roomId
    }

    private fun persistAttendance(roomId: UUID, attendanceMemberId: UUID, status: AttendanceStatus) {
        attendanceRepository.saveAndFlush(
            AttendanceEntity(
                roomId = roomId,
                memberId = attendanceMemberId,
                status = status,
                recorderMemberId = recorderMemberId,
                recordedAt = LocalDateTime.of(2026, 8, 10, 21, 0),
            ),
        )
    }

    private fun persistReview(roomId: UUID, targetMemberId: UUID) {
        reviewRepository.saveAndFlush(
            ReviewEntity(
                roomId = roomId,
                authorMemberId = memberId,
                targetMemberId = targetMemberId,
                rating = 5,
                visibleAt = LocalDateTime.of(2026, 8, 10, 22, 0),
                tags = listOf("좋은 질문을 해요"),
            ),
        )
    }
}
