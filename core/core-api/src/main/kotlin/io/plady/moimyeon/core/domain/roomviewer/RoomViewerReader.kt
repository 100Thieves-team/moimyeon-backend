package io.plady.moimyeon.core.domain.roomviewer

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionFinder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 뷰어 사실을 여러 개념에서 모아 그대로 돌려준다. 판정하지 않는다(MOI-500) —
// 버튼 판정은 화면이, 강제는 신청 경로의 Validator 가 갖는다.
//
// 사실 조회는 신청 가능 조건을 재구현하지 않는다. 제재는 MemberFinder.isActive, 참여 슬롯은
// ParticipationFinder.getSlots(슬롯 API 와 같은 집계), 대기 신청은
// RoomApplicationSubmissionFinder.getPendingApplicationQuota 가 막는 경로와 같은 집계로 답한다.
//
// 쿼리 수는 룸 수와 무관한 상수 5개다(룸별 2 + 회원 3). 비로그인이면 0개다.
@Component
class RoomViewerReader(
    private val participationFinder: ParticipationFinder,
    private val memberFinder: MemberFinder,
    private val roomApplicationSubmissionFinder: RoomApplicationSubmissionFinder,
) {
    @Transactional(readOnly = true)
    fun readAll(viewerMemberId: UUID?, roomIds: Collection<UUID>): Map<UUID, ViewerFacts?> {
        // 비로그인은 내려줄 사실 자체가 없어 조회할 이유가 없다.
        // 룸이 없으면 빈 IN 절이 쿼리에 들어가지 않게 여기서 끝낸다(RoomSearchReader 와 같은 이유).
        if (viewerMemberId == null || roomIds.isEmpty()) {
            return roomIds.associateWith { null }
        }

        val participations = participationFinder.getRoomParticipations(viewerMemberId, roomIds.toSet())
            .associateBy { it.roomId }
        val applications = roomApplicationSubmissionFinder.getLatestStatusByRooms(viewerMemberId, roomIds.toSet())
        // 룸과 무관한 축이라 페이지당 한 번만 읽는다.
        val memberFacts = ViewerMemberFacts(
            active = memberFinder.isActive(viewerMemberId),
            participationSlots = participationFinder.getSlots(viewerMemberId),
            pendingApplicationQuota = roomApplicationSubmissionFinder.getPendingApplicationQuota(viewerMemberId),
        )

        return roomIds.associateWith { roomId ->
            val participation = participations[roomId]
            ViewerFacts(
                room = ViewerRoomFacts(
                    host = participation?.host ?: false,
                    participating = participation?.joined ?: false,
                    removed = participation?.removed ?: false,
                    latestApplication = applications[roomId],
                ),
                member = memberFacts,
            )
        }
    }
}
