package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ProgressRailResponse
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.progress.ProgressBlock
import io.plady.moimyeon.core.domain.progress.RoomProgressService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomProgressFacade(
    private val progressService: RoomProgressService,
    private val memberService: MemberService,
) {
    fun getRail(memberId: UUID, roomId: UUID): ProgressRailResponse {
        val rail = progressService.getRail(memberId, roomId)
        val targetMemberIds = rail.blocks.filterIsInstance<ProgressBlock.Round>().map { it.targetMemberId }
        val nicknames = memberService.getMembers(targetMemberIds).associate { it.id to it.nickname.value }
        return ProgressRailResponse.from(rail, nicknames)
    }
}
