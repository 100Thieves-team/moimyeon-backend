package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.AttendanceResponse
import io.plady.moimyeon.core.api.controller.v1.response.ProgressRailResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomAttendancesResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomProgressCompletionResponse
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
    fun complete(memberId: UUID, roomId: UUID): RoomProgressCompletionResponse {
        return RoomProgressCompletionResponse.from(progressService.complete(memberId, roomId))
    }

    fun recordAttendances(memberId: UUID, roomId: UUID, attendances: List<Attendance>): RoomAttendancesResponse {
        val result = progressService.recordAttendances(memberId, roomId, attendances)
        return RoomAttendancesResponse.from(result, nicknamesOf(result.attendances.map(Attendance::memberId)))
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
