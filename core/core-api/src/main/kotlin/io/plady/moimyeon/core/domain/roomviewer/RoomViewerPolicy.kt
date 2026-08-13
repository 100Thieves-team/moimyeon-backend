package io.plady.moimyeon.core.domain.roomviewer

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import java.time.LocalDateTime

// §4 판정 우선순위(MOI-387)의 유일한 소유자.
//
//   1 비로그인 → 2 방장 → 3 참여 중 → 4 신청 대기 중
//   → 5 룸 상태 → 6 관계 이력 → 7 회원 상태 → 8 정원 도달 → 9 그 외
//
// 방장·참여자를 룸 상태보다 앞에 두는 이유는 이미 그 룸에 속한 사람에게는 신청 개념 자체가
// 없기 때문이다 — 취소된 룸의 방장도 `룸 관리` 로 들어가야 한다.
//
// 조회를 하지 않는다. 사실은 RoomViewerReader 가 모아서 넘기고 여기는 판정만 한다 —
// 그래야 관계 9종 × 룸 상태 5종을 DB 없이 전수로 검사할 수 있다.
object RoomViewerPolicy {
    fun decide(room: RoomApplicability, viewer: ViewerFacts?, now: LocalDateTime): RoomViewer {
        if (viewer == null) {
            return RoomViewer(ViewerRelation.ANONYMOUS, listOf(ViewerAction.LOGIN_REQUIRED), null)
        }

        // 관계는 사실 그대로 정한다. 차단됐는지와 무관하다 —
        // 취소된 룸을 반려당한 사람이 보면 relation=REJECTED, blockReason=ROOM_CANCELED 다.
        val relation = relationOf(viewer.room)

        // 우선순위 2~4. 룸에 속했거나 신청이 살아 있으면 룸 상태를 보지 않는다.
        when (relation) {
            ViewerRelation.HOST -> return RoomViewer(relation, listOf(ViewerAction.MANAGE_ROOM), null)
            ViewerRelation.PARTICIPANT -> return RoomViewer(relation, listOf(ViewerAction.VIEW_MY_ROOM), null)
            ViewerRelation.APPLIED -> return RoomViewer(
                relation,
                listOf(ViewerAction.VIEW_MY_APPLICATION, ViewerAction.WITHDRAW_APPLICATION),
                null,
            )

            else -> Unit
        }

        // 우선순위 5~7.
        val blockReason = roomBlockReason(room, now)
            ?: historyBlockReason(viewer.room)
            ?: memberBlockReason(viewer.member)
        if (blockReason != null) {
            return RoomViewer(relation, emptyList(), blockReason)
        }

        // 우선순위 8~9. 정원은 버튼 분기일 뿐이다.
        return RoomViewer(relation, listOf(applyAction(room)), null)
    }

    private fun relationOf(facts: ViewerRoomFacts): ViewerRelation = when {
        facts.host -> ViewerRelation.HOST
        facts.participating -> ViewerRelation.PARTICIPANT
        // 강퇴당한 사람의 신청은 ACCEPTED 로 남아 있다. 신청 상태보다 먼저 봐야 강퇴가 드러난다.
        facts.removed -> ViewerRelation.REMOVED
        else -> when (facts.latestApplication) {
            RoomApplicationStatus.PENDING -> ViewerRelation.APPLIED
            RoomApplicationStatus.WITHDRAWN -> ViewerRelation.WITHDRAWN
            RoomApplicationStatus.REJECTED -> ViewerRelation.REJECTED
            RoomApplicationStatus.ROOM_CANCELED,
            RoomApplicationStatus.ROOM_CONFIRMED,
            RoomApplicationStatus.SLOT_EXCEEDED,
            -> ViewerRelation.APPLICATION_CLOSED
            // 수락된 신청이 여기까지 왔다면 참여했다가 스스로 나간 사람이다(D4).
            // 자진 이탈은 재신청을 막지 않으므로 관계도 남기지 않는다.
            RoomApplicationStatus.ACCEPTED -> ViewerRelation.NONE
            null -> ViewerRelation.NONE
        }
    }

    private fun roomBlockReason(room: RoomApplicability, now: LocalDateTime): ViewerBlockReason? = when (room.status) {
        RoomStatus.CANCELED -> ViewerBlockReason.ROOM_CANCELED
        RoomStatus.COMPLETED -> ViewerBlockReason.ROOM_COMPLETED
        RoomStatus.CONFIRMED -> ViewerBlockReason.ROOM_CONFIRMED
        RoomStatus.IN_PROGRESS -> ViewerBlockReason.SCHEDULE_PASSED
        // 제출 경로(RoomValidator.validateAcceptingApplications)와 같은 술어여야 한다.
        // 갈리면 화면이 "신청할 수 있다"고 안내한 뒤 서버가 거부한다.
        RoomStatus.RECRUITING -> if (room.startAt.isAfter(now)) null else ViewerBlockReason.SCHEDULE_PASSED
    }

    private fun historyBlockReason(facts: ViewerRoomFacts): ViewerBlockReason? = when {
        facts.latestApplication == RoomApplicationStatus.REJECTED -> ViewerBlockReason.APPLICATION_REJECTED
        facts.removed -> ViewerBlockReason.REMOVED_FROM_ROOM
        else -> null
    }

    // 제재 → 참여 슬롯 → 신청 한도. 제출 경로(RoomApplicationSubmissionManager.submit)와 같은 순서다 —
    // 둘 다 초과면 "참여 중인 룸을 정리하라"가 더 정확한 안내다(MOI-427 D8).
    private fun memberBlockReason(facts: ViewerMemberFacts): ViewerBlockReason? = when {
        !facts.active -> ViewerBlockReason.MEMBER_SUSPENDED
        !facts.participationSlotAvailable -> ViewerBlockReason.PARTICIPATION_SLOT_EXCEEDED
        !facts.applicationQuotaAvailable -> ViewerBlockReason.APPLICATION_LIMIT_EXCEEDED
        else -> null
    }

    private fun applyAction(room: RoomApplicability): ViewerAction = if (room.full) {
        ViewerAction.APPLY_WAITLIST
    } else {
        ViewerAction.APPLY
    }
}
