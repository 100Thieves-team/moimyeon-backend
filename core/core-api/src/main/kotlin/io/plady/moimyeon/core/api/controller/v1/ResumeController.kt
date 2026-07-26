package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.response.AiSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.ResumeResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import kotlin.math.roundToInt

// TODO(이력서): 모킹 응답(고정). 실제 구현 시 파일 저장(PDF 검증)·AI 요약 비동기 생성·소유자(@LoginMember) 연결을 처리한다.
@MockApiProfile
@RestController
class ResumeController {
    // POST /v1/resumes — 이력서 PDF 업로드(multipart/form-data, field: file). 업로드 직후 AI 요약은 PROCESSING.
    @PostMapping("/v1/resumes")
    fun upload(
        @RequestParam("file") file: MultipartFile,
    ): ApiResponse<ResumeResponse> {
        return ApiResponse.success(
            ResumeResponse(
                resumeId = MOCK_RESUME_ID,
                fileName = file.originalFilename ?: "이력서.pdf",
                fileSize = file.size,
                fileSizeLabel = humanFileSize(file.size),
                status = "UPLOADED",
                aiSummary = AiSummaryResponse(status = "PROCESSING", text = null),
            ),
        )
    }

    // GET /v1/resumes/{resumeId} — 업로드 상태 + AI 요약. 목은 조회 시 요약 완료(DONE)를 반환(폴링 시뮬레이션).
    @GetMapping("/v1/resumes/{resumeId}")
    fun detail(
        @PathVariable resumeId: Long,
    ): ApiResponse<ResumeResponse> {
        return ApiResponse.success(
            ResumeResponse(
                resumeId = resumeId,
                fileName = "김민수_이력서.pdf",
                fileSize = 245760,
                fileSizeLabel = "240KB",
                status = "UPLOADED",
                aiSummary = AiSummaryResponse(
                    status = "DONE",
                    text = "결제 도메인 3년 차 프론트엔드 개발자. React·TypeScript 중심으로 일했고, 디자인 시스템 구축 경험이 있어요.",
                ),
            ),
        )
    }

    private fun humanFileSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.roundToInt()}KB"
        return "%.1fMB".format(kb / 1024.0)
    }

    companion object {
        private const val MOCK_RESUME_ID = 90001L
    }
}
