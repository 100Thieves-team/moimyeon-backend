package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.MemberMeResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MemberController(
    private val memberService: MemberService,
    private val profileService: ProfileService,
) {
    @GetMapping("/v1/members/me")
    fun me(
        @LoginMember currentMember: CurrentMember,
    ): ApiResponse<MemberMeResponse> {
        val member = memberService.getMember(currentMember.id)

        val profile = if (profileService.hasProfile(currentMember.id)) profileService.getProfile(currentMember.id) else null
        return ApiResponse.success(MemberMeResponse.of(member, profile))
    }
}
