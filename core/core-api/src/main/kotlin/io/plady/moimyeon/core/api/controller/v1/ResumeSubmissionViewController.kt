package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.ResumeOriginalViewUrlResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.room.ResumeOriginalViewService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@RestController
class ResumeSubmissionViewController(
    private val resumeOriginalViewService: ResumeOriginalViewService,
) {
    // GET /v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url — 원본 열람 URL 발급(MOI-414).
    // 참여자 명부가 내린 resumeSubmissionId 로 부른다. 매 호출마다 재검증하므로 API URL 을 따 둬도
    // 방이 끝나면 E1429 를 받는다. 응답의 presigned URL 만 5분 창이 남는다(D3-3·D3-8).
    @GetMapping("/v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url")
    fun viewUrl(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @PathVariable resumeSubmissionId: Long,
    ): ApiResponse<ResumeOriginalViewUrlResponse> {
        val viewUrl = resumeOriginalViewService.issueViewUrl(currentMember.id, roomId, resumeSubmissionId)
        return ApiResponse.success(ResumeOriginalViewUrlResponse.from(viewUrl))
    }
}
