package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime
import java.util.UUID

@Import(FailingRoomApplicationNotificationConfiguration::class)
class RoomApplicationNotificationRollbackIT(
    val roomApplicationManager: RoomApplicationManager,
    val roomRepository: RoomRepository,
    val roomApplicationRepository: RoomApplicationRepository,
    val participationRepository: ParticipationRepository,
    val jdbcTemplate: JdbcTemplate,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val applicantId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM outbox")
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        roomRepository.deleteById(roomId)
    }

    @Test
    fun `알림 의도 기록이 실패하면 참가 신청 수락도 함께 롤백된다`() {
        seedRoom()
        seedHost()
        val applicationId = seedPendingApplication()

        assertThatThrownBy { roomApplicationManager.accept(roomId, applicationId, hostId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("알림 의도 기록 실패")

        val application = roomApplicationRepository.findById(applicationId).orElseThrow()
        assertThat(application.status).isEqualTo(RoomApplicationStatus.PENDING)
        assertThat(application.pendingMemberId).isEqualTo(applicantId)
        assertThat(
            participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                applicantId,
                ParticipationStatus.JOINED,
            ),
        ).isFalse()
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Long::class.java))
            .isZero()
    }

    private fun seedRoom() {
        roomRepository.save(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = now.plusDays(3),
                durationMinutes = 60,
            ),
        )
    }

    private fun seedHost() {
        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
    }

    private fun seedPendingApplication(): Long = roomApplicationRepository.save(
        RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = applicantId,
            note = "실전처럼 준비하고 싶어요.",
            appliedAt = now,
            status = RoomApplicationStatus.PENDING,
            pendingMemberId = applicantId,
        ),
    ).id
}

@TestConfiguration(proxyBeanMethods = false)
class FailingRoomApplicationNotificationConfiguration {
    @Bean
    fun failingRoomApplicationAcceptedListener() = FailingRoomApplicationAcceptedListener()
}

class FailingRoomApplicationAcceptedListener {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun fail(event: RoomApplicationAcceptedEvent) {
        throw IllegalStateException("알림 의도 기록 실패")
    }
}
