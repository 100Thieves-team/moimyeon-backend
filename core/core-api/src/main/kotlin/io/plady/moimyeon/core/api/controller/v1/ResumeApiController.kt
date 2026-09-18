package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateResumeRequest
import io.plady.moimyeon.core.api.controller.v1.response.ResumeResponse
import io.plady.moimyeon.core.api.controller.v1.response.ResumesResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.resume.ResumeService
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
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@RestController
class ResumeApiController(
    private val resumeService: ResumeService,
) {
    @GetMapping("/v1/members/me/resumes")
    fun resumes(
        @LoginMember currentMember: CurrentMember,
    ): ApiResponse<ResumesResponse> {
        return ApiResponse.success(ResumesResponse.from(resumeService.getStored(currentMember.id)))
    }

    @GetMapping("/v1/members/me/resumes/{resumeId}")
    fun resume(
        @LoginMember currentMember: CurrentMember,
        @PathVariable resumeId: UUID,
    ): ApiResponse<ResumeResponse> {
        return ApiResponse.success(ResumeResponse.from(resumeService.get(currentMember.id, resumeId)))
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
        val resumeId = resumeService.register(currentMember.id, CreateResumeRequest(file).toUpload())
        return ApiResponse.success(ResumeResponse.from(resumeService.get(currentMember.id, resumeId)))
    }

    @PostMapping("/v1/members/me/resumes/{resumeId}/summary-retries")
    fun retrySummary(
        @LoginMember currentMember: CurrentMember,
        @PathVariable resumeId: UUID,
    ): ApiResponse<ResumeResponse> {
        resumeService.retrySummary(currentMember.id, resumeId)
        return ApiResponse.success(ResumeResponse.from(resumeService.get(currentMember.id, resumeId)))
    }

    @PostMapping("/v1/members/me/resumes/{resumeId}/make-default")
    fun makeDefault(
        @LoginMember currentMember: CurrentMember,
        @PathVariable resumeId: UUID,
    ): ApiResponse<Any> {
        resumeService.makeDefault(currentMember.id, resumeId)
        return ApiResponse.success()
    }

    @DeleteMapping("/v1/members/me/resumes/{resumeId}")
    fun delete(
        @LoginMember currentMember: CurrentMember,
        @PathVariable resumeId: UUID,
    ): ApiResponse<Any> {
        resumeService.delete(currentMember.id, resumeId)
        return ApiResponse.success()
    }
}
