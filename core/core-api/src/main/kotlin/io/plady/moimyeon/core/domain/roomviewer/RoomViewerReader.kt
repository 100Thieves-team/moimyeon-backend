package io.plady.moimyeon.core.domain.roomviewer

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionFinder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 뷰어 관계 판정에 필요한 사실을 여러 개념에서 모아 RoomViewerPolicy 에 넘긴다.
// 판정은 하지 않는다 — 규칙은 Policy 한 곳이 갖는다.
//
// 신청 가능 조건을 여기서 재구현하지 않는다. 제재는 MemberFinder.isActive, 참여 슬롯은
// ParticipationFinder.hasAvailableSlot(MOI-427), 신청 한도는
// RoomApplicationSubmissionFinder.hasAvailableQuota 가 막는 경로와 같은 규칙으로 답한다.
//
// 쿼리 수는 룸 수와 무관한 상수 5개다(룸별 2 + 회원 3). 비로그인이면 0개다.
@Component
class RoomViewerReader(
    private val participationFinder: ParticipationFinder,
    private val memberFinder: MemberFinder,
    private val roomApplicationSubmissionFinder: RoomApplicationSubmissionFinder,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun readAll(viewerMemberId: UUID?, rooms: Map<UUID, RoomApplicability>): Map<UUID, RoomViewer> {
        val now = LocalDateTime.now(clock)

        // 비로그인은 판정 결과가 어차피 ANONYMOUS 하나라 조회할 이유가 없다.
        // 룸이 없으면 빈 IN 절이 쿼리에 들어가지 않게 여기서 끝낸다(RoomSearchReader 와 같은 이유).
        if (viewerMemberId == null || rooms.isEmpty()) {
            return rooms.mapValues { (_, room) -> RoomViewerPolicy.decide(room, null, now) }
        }

        val participations = participationFinder.getRoomParticipations(viewerMemberId, rooms.keys)
            .associateBy { it.roomId }
        val applications = roomApplicationSubmissionFinder.getLatestStatusByRooms(viewerMemberId, rooms.keys)
        // 룸과 무관한 축이라 페이지당 한 번만 읽는다.
        val memberFacts = ViewerMemberFacts(
            active = memberFinder.isActive(viewerMemberId),
            participationSlotAvailable = participationFinder.hasAvailableSlot(viewerMemberId),
            applicationQuotaAvailable = roomApplicationSubmissionFinder.hasAvailableQuota(viewerMemberId),
        )

        return rooms.mapValues { (roomId, room) ->
            val participation = participations[roomId]
            val facts = ViewerFacts(
                room = ViewerRoomFacts(
                    host = participation?.host ?: false,
                    participating = participation?.joined ?: false,
                    removed = participation?.removed ?: false,
                    latestApplication = applications[roomId],
                ),
                member = memberFacts,
            )
            RoomViewerPolicy.decide(room, facts, now)
        }
    }
}
