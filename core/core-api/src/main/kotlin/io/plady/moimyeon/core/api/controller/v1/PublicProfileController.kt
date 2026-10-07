package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileResponse
import io.plady.moimyeon.core.api.facade.PublicProfileFacade
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class PublicProfileController(
    private val publicProfileFacade: PublicProfileFacade,
) {
    @GetMapping("/v1/members/{memberId}/profile")
    fun publicProfile(
        @PathVariable memberId: UUID,
    ): ApiResponse<PublicProfileResponse> {
        return ApiResponse.success(publicProfileFacade.get(memberId))
    }
}
