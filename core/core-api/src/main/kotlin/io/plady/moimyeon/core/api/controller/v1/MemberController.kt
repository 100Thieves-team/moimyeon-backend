package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.UpdateNicknameRequest
import io.plady.moimyeon.core.api.controller.v1.response.MemberMeResponse
import io.plady.moimyeon.core.api.controller.v1.response.NicknameAvailabilityResponse
import io.plady.moimyeon.core.api.controller.v1.response.NicknameSuggestionResponse
import io.plady.moimyeon.core.api.facade.MemberFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class MemberController(
    private val memberService: MemberService,
    private val memberFacade: MemberFacade,
) {
    @GetMapping("/v1/members/me")
    fun me(
        @LoginMember currentMember: CurrentMember,
    ): ApiResponse<MemberMeResponse> {
        return ApiResponse.success(memberFacade.me(currentMember.id))
    }

    @PutMapping("/v1/members/me/nickname")
    fun updateNickname(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: UpdateNicknameRequest,
    ): ApiResponse<Any> {
        memberService.changeNickname(currentMember.id, request.toNickname())
        return ApiResponse.success()
    }

    @GetMapping("/v1/nicknames/suggestion")
    fun suggestNickname(): ApiResponse<NicknameSuggestionResponse> {
        return ApiResponse.success(NicknameSuggestionResponse(memberService.suggestNickname().value))
    }

    @GetMapping("/v1/nicknames/availability")
    fun nicknameAvailability(
        @RequestParam nickname: String,
    ): ApiResponse<NicknameAvailabilityResponse> {
        return ApiResponse.success(NicknameAvailabilityResponse(memberService.isNicknameAvailable(nickname)))
    }
}
