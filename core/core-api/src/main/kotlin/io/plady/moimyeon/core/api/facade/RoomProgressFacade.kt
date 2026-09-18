package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.AttendanceResponse
import io.plady.moimyeon.core.api.controller.v1.response.ProgressRailResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomProgressStartResponse
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.progress.Attendance
import io.plady.moimyeon.core.domain.progress.ProgressBlock
import io.plady.moimyeon.core.domain.progress.RoomProgressService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomProgressFacade(
    private val progressService: RoomProgressService,
    private val memberService: MemberService,
) {
    // 출석 명단에 닉네임을 붙인다(MOI-496). 탈퇴 회원은 조회에 안 나와 응답 조립에서 대체 표기로 채워진다.
    fun start(memberId: UUID, roomId: UUID, attendances: List<Attendance>): RoomProgressStartResponse {
        val result = progressService.start(memberId, roomId, attendances)
        return RoomProgressStartResponse.from(result, nicknamesOf(result.attendances.map(Attendance::memberId)))
    }

    fun getMyAttendance(memberId: UUID, roomId: UUID): AttendanceResponse {
        val attendance = progressService.getMyAttendance(memberId, roomId)
        return AttendanceResponse.from(attendance, nicknamesOf(listOf(attendance.memberId)))
    }

    fun getRail(memberId: UUID, roomId: UUID): ProgressRailResponse {
        val rail = progressService.getRail(memberId, roomId)
        val targetMemberIds = rail.blocks.filterIsInstance<ProgressBlock.Round>().map { it.targetMemberId }
        return ProgressRailResponse.from(rail, nicknamesOf(targetMemberIds))
    }

    private fun nicknamesOf(memberIds: List<UUID>): Map<UUID, String> = memberService.getMembers(memberIds).associate { it.id to it.nickname.value }
}
