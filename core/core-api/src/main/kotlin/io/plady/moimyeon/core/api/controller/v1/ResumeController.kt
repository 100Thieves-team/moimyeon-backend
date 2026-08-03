package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateResumeRequest
import io.plady.moimyeon.core.api.controller.v1.response.ResumeAiSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.ResumeAiSummaryStatus
import io.plady.moimyeon.core.api.controller.v1.response.ResumeFileResponse
import io.plady.moimyeon.core.api.controller.v1.response.ResumeResponse
import io.plady.moimyeon.core.api.controller.v1.response.ResumesResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.UUID

// local 프론트엔드 개발용 고정 목. local-dev 이상에서는 ResumeApiController가 실제 S3·Bedrock 흐름을 제공한다.
@Profile("local")
@RestController
class ResumeController {
    private val defaultResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000101")
    private val commerceResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000102")
    private val processingResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000103")
    private val deletedResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000104")
    private val failedResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000105")
    private val createdResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000106")
    private val knownResumeIds: Set<UUID> =
        setOf(defaultResumeId, commerceResumeId, processingResumeId, deletedResumeId, failedResumeId, createdResumeId)

    @GetMapping("/v1/members/me/resumes")
    fun resumes(
        @LoginMember currentMember: CurrentMember,
    ): ApiResponse<ResumesResponse> {
        return ApiResponse.success(mockResumes())
    }

    @GetMapping("/v1/members/me/resumes/{resumeId}")
    fun resume(
        @LoginMember currentMember: CurrentMember,
        @PathVariable resumeId: UUID,
    ): ApiResponse<ResumeResponse> {
        val resume = mockResumes().resumes.find { it.resumeId == resumeId }
            ?: throw CoreException(CoreErrorType.RESUME_NOT_FOUND)
        return ApiResponse.success(resume)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(
        "/v1/members/me/resumes",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun create(
        @LoginMember currentMember: CurrentMember,
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<ResumeResponse> {
        val upload = CreateResumeRequest(file).toUpload()
        return ApiResponse.success(
            ResumeResponse(
                resumeId = createdResumeId,
                name = upload.originalName,
                file = ResumeFileResponse(
                    originalName = upload.originalName,
                    sizeBytes = upload.content.size.toLong(),
                    contentType = upload.contentType,
                ),
                aiSummary = ResumeAiSummaryResponse(
                    status = ResumeAiSummaryStatus.DONE,
                    text = "Kotlin·Spring 기반 백엔드 개발 경험과 결제 도메인 프로젝트를 중심으로 한 이력서.",
                ),
                isDefault = false,
                registeredAt = LocalDateTime.of(2026, 8, 1, 15, 30),
            ),
        )
    }

    @PostMapping("/v1/members/me/resumes/{resumeId}/summary-retries")
    fun retrySummary(
        @LoginMember currentMember: CurrentMember,
        @PathVariable resumeId: UUID,
    ): ApiResponse<ResumeResponse> {
        if (resumeId != failedResumeId) {
            if (resumeId !in knownResumeIds) throw CoreException(CoreErrorType.RESUME_NOT_FOUND)
            throw CoreException(CoreErrorType.RESUME_SUMMARY_NOT_RETRYABLE)
        }
        val resume = mockResumes().resumes.first { it.resumeId == failedResumeId }
        return ApiResponse.success(
            resume.copy(
                aiSummary = ResumeAiSummaryResponse(
                    status = ResumeAiSummaryStatus.DONE,
                    text = "재시도에 성공해 생성한 백엔드 이력서 요약.",
                ),
            ),
        )
    }

    @DeleteMapping("/v1/members/me/resumes/{resumeId}")
    fun delete(
        @LoginMember currentMember: CurrentMember,
        @PathVariable resumeId: UUID,
    ): ApiResponse<Any> {
        if (resumeId !in knownResumeIds) {
            throw CoreException(CoreErrorType.RESUME_NOT_FOUND)
        }
        return ApiResponse.success()
    }

    private fun mockResumes(): ResumesResponse {
        return ResumesResponse(
            maxCount = MAX_RESUME_COUNT,
            resumes = listOf(
                ResumeResponse(
                    resumeId = defaultResumeId,
                    name = "든든한곰_이력서.pdf",
                    file = ResumeFileResponse(
                        originalName = "든든한곰_이력서.pdf",
                        sizeBytes = 217_088,
                        contentType = MediaType.APPLICATION_PDF_VALUE,
                    ),
                    aiSummary = ResumeAiSummaryResponse(
                        status = ResumeAiSummaryStatus.DONE,
                        text = "핀테크 백엔드 3년 차. 결제 정산 배치·대사와 Kotlin·Spring 경험.",
                    ),
                    isDefault = true,
                    registeredAt = LocalDateTime.of(2026, 7, 12, 9, 30),
                ),
                ResumeResponse(
                    resumeId = commerceResumeId,
                    name = "든든한곰_이력서_커머스.pdf",
                    file = ResumeFileResponse(
                        originalName = "든든한곰_이력서_커머스.pdf",
                        sizeBytes = 202_752,
                        contentType = MediaType.APPLICATION_PDF_VALUE,
                    ),
                    aiSummary = ResumeAiSummaryResponse(
                        status = ResumeAiSummaryStatus.DONE,
                        text = "커머스 주문·재고 프로젝트 중심. 대용량 배치 처리 경험을 강조한 이력서.",
                    ),
                    isDefault = false,
                    registeredAt = LocalDateTime.of(2026, 6, 28, 18, 10),
                ),
                ResumeResponse(
                    resumeId = processingResumeId,
                    name = "든든한곰_이력서_시스템설계.pdf",
                    file = ResumeFileResponse(
                        originalName = "든든한곰_이력서_시스템설계.pdf",
                        sizeBytes = 229_376,
                        contentType = MediaType.APPLICATION_PDF_VALUE,
                    ),
                    aiSummary = ResumeAiSummaryResponse(
                        status = ResumeAiSummaryStatus.PROCESSING,
                        text = null,
                    ),
                    isDefault = false,
                    registeredAt = LocalDateTime.of(2026, 8, 1, 14, 20),
                ),
                ResumeResponse(
                    resumeId = failedResumeId,
                    name = "든든한곰_이력서_재시도.pdf",
                    file = ResumeFileResponse(
                        originalName = "든든한곰_이력서_재시도.pdf",
                        sizeBytes = 211_968,
                        contentType = MediaType.APPLICATION_PDF_VALUE,
                    ),
                    aiSummary = ResumeAiSummaryResponse(
                        status = ResumeAiSummaryStatus.FAILED,
                        text = null,
                    ),
                    isDefault = false,
                    registeredAt = LocalDateTime.of(2026, 8, 1, 14, 30),
                ),
            ),
        )
    }
}

private const val MAX_RESUME_COUNT = 10
