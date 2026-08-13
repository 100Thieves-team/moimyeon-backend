package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.trust.RoomReviewStatus
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
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
class RoomParticipationReaderIT(
    private val reader: RoomParticipationReader,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val reviewRepository: ReviewRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000436")
    private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000437")
    private val recorderMemberId = UUID.fromString("00000000-0000-0000-0000-000000000438")

    @Test
    fun `현재 참여 중인 룸과 완료한 룸을 상태와 일정 순서로 구분한다`() {
        val confirmedRoomId = persistRoom(
            "확정된 백엔드 모의면접",
            RoomStatus.CONFIRMED,
            LocalDateTime.of(2026, 8, 18, 19, 0),
        )
        persistParticipation(confirmedRoomId, memberId)
        persistParticipation(confirmedRoomId, otherMemberId)

        val recruitingRoomId = persistRoom(
            "참여 중인 백엔드 모의면접",
            RoomStatus.RECRUITING,
            LocalDateTime.of(2026, 8, 19, 19, 0),
        )
        persistParticipation(recruitingRoomId, memberId)

        val writableRoomId = persistCompletedRoom(
            "후기 작성할 백엔드 모의면접",
            LocalDateTime.of(2026, 8, 10, 19, 0),
            AttendanceStatus.ATTENDED,
        )
        persistAttendance(writableRoomId, otherMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(writableRoomId, recorderMemberId, AttendanceStatus.ATTENDED)
        persistReview(writableRoomId, otherMemberId)

        val writtenRoomId = persistCompletedRoom(
            "후기 작성한 백엔드 모의면접",
            LocalDateTime.of(2026, 8, 8, 19, 0),
            AttendanceStatus.ATTENDED,
        )
        persistAttendance(writtenRoomId, otherMemberId, AttendanceStatus.ATTENDED)
        persistReview(writtenRoomId, otherMemberId)

        val absentRoomId = persistCompletedRoom(
            "불참한 완료 백엔드 모의면접",
            LocalDateTime.of(2026, 8, 6, 19, 0),
            AttendanceStatus.ABSENT,
        )
        persistAttendance(absentRoomId, otherMemberId, AttendanceStatus.ATTENDED)

        val noTargetRoomId = persistRoom(
            "후기 대상 없는 백엔드 모의면접",
            RoomStatus.COMPLETED,
            LocalDateTime.of(2026, 8, 4, 19, 0),
        )
        persistParticipation(noTargetRoomId, memberId)
        persistAttendance(noTargetRoomId, memberId, AttendanceStatus.ATTENDED)

        val canceledRoomId = persistRoom(
            "취소된 백엔드 모의면접",
            RoomStatus.CANCELED,
            LocalDateTime.of(2026, 8, 20, 19, 0),
        )
        persistParticipation(canceledRoomId, memberId)

        val leftRoomId = persistRoom(
            "나간 백엔드 모의면접",
            RoomStatus.RECRUITING,
            LocalDateTime.of(2026, 8, 21, 19, 0),
        )
        persistParticipation(leftRoomId, memberId, ParticipationStatus.LEFT)

        val result = reader.getHistory(memberId)

        assertThat(result.participatingRooms.map { it.room.id })
            .containsExactly(confirmedRoomId, recruitingRoomId)
        assertThat(result.participatingRooms.map { it.participantCount }).containsExactly(2, 1)
        assertThat(result.completedRooms.map { it.room.id })
            .containsExactly(writableRoomId, writtenRoomId, absentRoomId, noTargetRoomId)
        assertThat(result.completedRooms.map { it.attendedParticipantCount }).containsExactly(3, 2, 1, 1)
        assertThat(result.completedRooms.map { it.reviewStatus })
            .containsExactly(
                RoomReviewStatus.WRITABLE,
                RoomReviewStatus.WRITTEN,
                RoomReviewStatus.NOT_ELIGIBLE_ABSENT,
                RoomReviewStatus.NOT_ELIGIBLE_NO_TARGET,
            )
    }

    @Test
    fun `참여 이력이 없으면 두 구분을 모두 빈 목록으로 반환한다`() {
        val result = reader.getHistory(memberId)

        assertThat(result.participatingRooms).isEmpty()
        assertThat(result.completedRooms).isEmpty()
    }

    private fun persistCompletedRoom(
        title: String,
        startAt: LocalDateTime,
        attendanceStatus: AttendanceStatus,
    ): UUID {
        val roomId = persistRoom(title, RoomStatus.COMPLETED, startAt)
        persistParticipation(roomId, memberId)
        persistParticipation(roomId, otherMemberId)
        persistAttendance(roomId, memberId, attendanceStatus)
        return roomId
    }

    private fun persistRoom(title: String, status: RoomStatus, startAt: LocalDateTime): UUID {
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
                startAt = startAt,
                durationMinutes = 60,
            ),
        )
        if (status != RoomStatus.RECRUITING) {
            entityManager.createNativeQuery("update room set status = :status where id = :roomId")
                .setParameter("status", status.name)
                .setParameter("roomId", roomId)
                .executeUpdate()
            entityManager.clear()
        }
        return roomId
    }

    private fun persistParticipation(
        roomId: UUID,
        participantMemberId: UUID,
        status: ParticipationStatus = ParticipationStatus.JOINED,
    ) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = participantMemberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = status,
                joinedAt = LocalDateTime.of(2026, 8, 1, 12, 0),
                leftByMemberId = participantMemberId.takeIf { status == ParticipationStatus.LEFT },
                leftAt = LocalDateTime.of(2026, 8, 2, 12, 0).takeIf { status == ParticipationStatus.LEFT },
            ),
        )
    }

    private fun persistAttendance(roomId: UUID, participantMemberId: UUID, status: AttendanceStatus) {
        attendanceRepository.saveAndFlush(
            AttendanceEntity(
                roomId = roomId,
                memberId = participantMemberId,
                status = status,
                recorderMemberId = recorderMemberId,
                recordedAt = LocalDateTime.of(2026, 8, 1, 12, 0),
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
                visibleAt = LocalDateTime.of(2026, 8, 8, 22, 0),
                tags = listOf("좋은 질문을 해요"),
            ),
        )
    }
}
