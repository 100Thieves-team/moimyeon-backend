package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.ProgressRailResponse
import io.plady.moimyeon.core.api.facade.RoomProgressFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Profile("dev")
@RestController
class ProgressRailController(
    private val progressFacade: RoomProgressFacade,
) {
    @GetMapping("/v1/progress-rails")
    fun rail(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
    ): ApiResponse<ProgressRailResponse> {
        return ApiResponse.success(progressFacade.getRail(currentMember.id, roomId))
    }
}
