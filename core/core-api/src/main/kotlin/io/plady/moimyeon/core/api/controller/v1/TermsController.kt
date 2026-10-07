package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.TermsListResponse
import io.plady.moimyeon.core.api.controller.v1.response.TermsResponse
import io.plady.moimyeon.core.domain.terms.TermsService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class TermsController(
    private val termsService: TermsService,
) {
    @GetMapping("/v1/terms")
    fun terms(): ApiResponse<TermsListResponse> {
        return ApiResponse.success(TermsListResponse(termsService.getActiveTerms().map(TermsResponse::from)))
    }

    @GetMapping("/v1/terms/{termsId}")
    fun termsDetail(@PathVariable termsId: UUID): ApiResponse<TermsResponse> {
        return ApiResponse.success(TermsResponse.from(termsService.getTerms(termsId)))
    }
}
