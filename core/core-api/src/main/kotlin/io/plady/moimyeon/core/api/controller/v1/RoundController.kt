package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.RoundScreenResponse
import io.plady.moimyeon.core.api.facade.RoundFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoundController(
    private val roundFacade: RoundFacade,
) {
    @GetMapping("/v1/rounds")
    fun screen(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
        @RequestParam intervieweeMemberId: UUID,
    ): ApiResponse<RoundScreenResponse> {
        return ApiResponse.success(roundFacade.getScreen(currentMember.id, roomId, intervieweeMemberId))
    }
}
