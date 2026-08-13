package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.resume.ResumeFileStore
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@Service
class ResumeOriginalViewService(
    private val participationValidator: ParticipationValidator,
    private val resumeOriginalViewFinder: ResumeOriginalViewFinder,
    private val resumeFileStore: ResumeFileStore,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 뷰어 게이트(제3자 E1419)가 열람 게이트(E1429)보다 먼저다 - 룸에 속하지 않은 사람에게
    // 룸 상태나 제출의 존재 여부를 흘리지 않는다. 본인 제출도 같은 게이트를 탄다(D3-7).
    fun issueViewUrl(viewerMemberId: UUID, roomId: UUID, resumeSubmissionId: Long): ResumeOriginalViewUrl {
        participationValidator.validateParticipant(roomId, viewerMemberId)
        val file = resumeOriginalViewFinder.getViewableFile(roomId, resumeSubmissionId)
        val url = resumeFileStore.issueViewUrl(file, VIEW_URL_TTL)

        // 조회 이력 테이블은 후속(D3-4): 발급 사실은 로그로만 남긴다.
        log.info(
            "Resume original view URL issued: viewerMemberId={}, roomId={}, resumeSubmissionId={}",
            viewerMemberId,
            roomId,
            resumeSubmissionId,
        )
        return ResumeOriginalViewUrl(url = url, expiresAt = LocalDateTime.now(clock).plus(VIEW_URL_TTL))
    }

    companion object {
        // D3-3: PDF 뷰어 로딩까지 감안한 최소선. 발급 뒤에는 권한이 회수돼도 이 창만큼 살아 있다.
        private val VIEW_URL_TTL: Duration = Duration.ofMinutes(5)
    }
}
