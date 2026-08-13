package io.plady.moimyeon.core.domain.roomviewer

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

// §4 판정 우선순위(MOI-387)의 전부를 여기서 고정한다. 조회가 없는 순수 판정이라
// 관계 9종 × 룸 상태 5종을 DB 없이 전수로 돌 수 있다.
class RoomViewerPolicyTest {
    @Test
    fun `비로그인이면 룸 상태와 무관하게 로그인 유도만 내린다`() {
        RoomStatus.entries.forEach { status ->
            val viewer = RoomViewerPolicy.decide(room(status = status, startAt = PAST), null, NOW)

            assertThat(viewer.relation).describedAs("$status").isEqualTo(ViewerRelation.ANONYMOUS)
            assertThat(viewer.actions).describedAs("$status").containsExactly(ViewerAction.LOGIN_REQUIRED)
            assertThat(viewer.blockReason).describedAs("$status").isNull()
        }
    }

    @Test
    fun `방장은 취소·완료된 룸에서도 룸 관리를 유지하고 차단이 아니다`() {
        listOf(RoomStatus.CANCELED, RoomStatus.COMPLETED).forEach { status ->
            val viewer = RoomViewerPolicy.decide(room(status = status, startAt = PAST), host(), NOW)

            assertThat(viewer.relation).describedAs("$status").isEqualTo(ViewerRelation.HOST)
            assertThat(viewer.actions).describedAs("$status").containsExactly(ViewerAction.MANAGE_ROOM)
            assertThat(viewer.blockReason).describedAs("$status").isNull()
        }
    }

    @Test
    fun `참여자는 취소·완료된 룸에서도 내 룸 보기를 유지한다`() {
        listOf(RoomStatus.CANCELED, RoomStatus.COMPLETED).forEach { status ->
            val viewer = RoomViewerPolicy.decide(room(status = status, startAt = PAST), participant(), NOW)

            assertThat(viewer.relation).describedAs("$status").isEqualTo(ViewerRelation.PARTICIPANT)
            assertThat(viewer.actions).describedAs("$status").containsExactly(ViewerAction.VIEW_MY_ROOM)
            assertThat(viewer.blockReason).describedAs("$status").isNull()
        }
    }

    @Test
    fun `대기 중 신청자에게는 신청 조회를 주 행동으로 철회와 함께 내린다`() {
        val viewer = RoomViewerPolicy.decide(room(), facts(application = RoomApplicationStatus.PENDING), NOW)

        assertThat(viewer.relation).isEqualTo(ViewerRelation.APPLIED)
        // 순서가 계약이다 — 첫 원소가 주 버튼이다(§5 불변식 2).
        assertThat(viewer.actions)
            .containsExactly(ViewerAction.VIEW_MY_APPLICATION, ViewerAction.WITHDRAW_APPLICATION)
        assertThat(viewer.blockReason).isNull()
    }

    @Test
    fun `룸 상태 차단은 관계 이력 차단보다 먼저 잡는다`() {
        val viewer = RoomViewerPolicy.decide(
            room(status = RoomStatus.CANCELED),
            facts(application = RoomApplicationStatus.REJECTED),
            NOW,
        )

        assertThat(viewer.relation).isEqualTo(ViewerRelation.REJECTED)
        assertThat(viewer.actions).isEmpty()
        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.ROOM_CANCELED)
    }

    @Test
    fun `관계 이력 차단은 회원 상태 차단보다 먼저 잡는다`() {
        val viewer = RoomViewerPolicy.decide(
            room(),
            facts(removed = true, application = RoomApplicationStatus.ACCEPTED, active = false),
            NOW,
        )

        assertThat(viewer.relation).isEqualTo(ViewerRelation.REMOVED)
        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.REMOVED_FROM_ROOM)
    }

    @Test
    fun `제재는 참여 슬롯보다 먼저 잡는다`() {
        val viewer = RoomViewerPolicy.decide(room(), facts(active = false, slotAvailable = false), NOW)

        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.MEMBER_SUSPENDED)
    }

    @Test
    fun `참여 슬롯은 신청 한도보다 먼저 잡는다`() {
        val viewer = RoomViewerPolicy.decide(room(), facts(slotAvailable = false, quotaAvailable = false), NOW)

        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.PARTICIPATION_SLOT_EXCEEDED)
    }

    @Test
    fun `정원이 차도 확정 전이면 차단이 아니라 대기 신청이다`() {
        val viewer = RoomViewerPolicy.decide(room(currentParticipants = 8, maxCapacity = 8), facts(), NOW)

        assertThat(viewer.relation).isEqualTo(ViewerRelation.NONE)
        assertThat(viewer.actions).containsExactly(ViewerAction.APPLY_WAITLIST)
        assertThat(viewer.blockReason).isNull()
    }

    @Test
    fun `정원이 찼어도 회원 상태 차단이 먼저 잡는다`() {
        val viewer = RoomViewerPolicy.decide(
            room(currentParticipants = 8, maxCapacity = 8),
            facts(quotaAvailable = false),
            NOW,
        )

        assertThat(viewer.actions).isEmpty()
        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.APPLICATION_LIMIT_EXCEEDED)
    }

    @Test
    fun `일정이 시작 시각과 같으면 경과로 본다`() {
        // 제출 경로(RoomValidator)가 startAt.isAfter(now) 를 요구하므로 같은 술어여야 한다.
        val viewer = RoomViewerPolicy.decide(room(startAt = NOW), facts(), NOW)

        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.SCHEDULE_PASSED)
    }

    @Test
    fun `룸 상태 차단이 일정 경과보다 먼저 잡는다`() {
        val viewer = RoomViewerPolicy.decide(room(status = RoomStatus.CANCELED, startAt = PAST), facts(), NOW)

        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.ROOM_CANCELED)
    }

    @Test
    fun `진행 중인 룸은 일정 경과로 차단한다`() {
        // D2 — IN_PROGRESS 면 일정이 이미 시작됐다. blockReason 값을 늘리지 않는다.
        val viewer = RoomViewerPolicy.decide(room(status = RoomStatus.IN_PROGRESS, startAt = PAST), facts(), NOW)

        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.SCHEDULE_PASSED)
    }

    @Test
    fun `자진 이탈한 참여자는 무관계가 되어 다시 신청할 수 있다`() {
        // D4 — 강퇴가 아닌 이탈은 재신청을 막지 않는다(제출 경로의 existsRemovalHistory 와 같은 기준).
        val viewer = RoomViewerPolicy.decide(room(), facts(application = RoomApplicationStatus.ACCEPTED), NOW)

        assertThat(viewer.relation).isEqualTo(ViewerRelation.NONE)
        assertThat(viewer.actions).containsExactly(ViewerAction.APPLY)
        assertThat(viewer.blockReason).isNull()
    }

    @Test
    fun `강퇴당한 사람은 신청 이력이 수락이어도 강퇴로 표시된다`() {
        val viewer = RoomViewerPolicy.decide(
            room(),
            facts(removed = true, application = RoomApplicationStatus.ACCEPTED),
            NOW,
        )

        assertThat(viewer.relation).isEqualTo(ViewerRelation.REMOVED)
        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.REMOVED_FROM_ROOM)
    }

    @Test
    fun `참여 슬롯이 찬 채로 끝난 신청은 슬롯 초과로 차단된다`() {
        // D1 — SLOT_EXCEEDED 는 시스템이 끝낸 신청이므로 관계는 APPLICATION_CLOSED 다.
        val viewer = RoomViewerPolicy.decide(
            room(),
            facts(application = RoomApplicationStatus.SLOT_EXCEEDED, slotAvailable = false),
            NOW,
        )

        assertThat(viewer.relation).isEqualTo(ViewerRelation.APPLICATION_CLOSED)
        assertThat(viewer.blockReason).isEqualTo(ViewerBlockReason.PARTICIPATION_SLOT_EXCEEDED)
    }

    @Test
    fun `시스템이 끝낸 신청은 슬롯이 비면 다시 신청할 수 있다`() {
        // D1 — 재신청 차단(§4.8)은 REJECTED 만 본다. 슬롯이 풀리면 다시 신청할 수 있어야 한다.
        val viewer = RoomViewerPolicy.decide(
            room(),
            facts(application = RoomApplicationStatus.SLOT_EXCEEDED),
            NOW,
        )

        assertThat(viewer.relation).isEqualTo(ViewerRelation.APPLICATION_CLOSED)
        assertThat(viewer.actions).containsExactly(ViewerAction.APPLY)
        assertThat(viewer.blockReason).isNull()
    }

    @Test
    fun `모든 관계와 룸 상태 조합에서 actions 가 비어 있는 것과 blockReason 이 있는 것이 일치한다`() {
        forEachCombination { relation, status, viewer ->
            assertThat(viewer.actions.isEmpty())
                .describedAs("$relation × $status → $viewer")
                .isEqualTo(viewer.blockReason != null)
        }
    }

    @Test
    fun `관계가 방장·참여자·대기 신청자면 룸 상태와 무관하게 주 행동이 같다`() {
        val primaryByRelation = mapOf(
            ViewerRelation.HOST to ViewerAction.MANAGE_ROOM,
            ViewerRelation.PARTICIPANT to ViewerAction.VIEW_MY_ROOM,
            ViewerRelation.APPLIED to ViewerAction.VIEW_MY_APPLICATION,
        )

        forEachCombination { relation, status, viewer ->
            primaryByRelation[relation]?.let { expected ->
                assertThat(viewer.actions.firstOrNull())
                    .describedAs("$relation × $status")
                    .isEqualTo(expected)
            }
        }
    }

    @Test
    fun `정원이 차도 어떤 조합에서도 차단 사유가 달라지지 않는다`() {
        // §5 불변식 3 — 정원 도달은 APPLY 냐 APPLY_WAITLIST 냐의 분기일 뿐 차단 조건이 아니다.
        // 정원만 다른 두 룸의 blockReason 이 같아야 한다.
        ViewerRelation.entries.forEach { relation ->
            RoomStatus.entries.forEach { status ->
                val startAt = if (status == RoomStatus.RECRUITING) FUTURE else PAST
                val roomy = RoomViewerPolicy.decide(
                    room(status = status, startAt = startAt, currentParticipants = 3, maxCapacity = 8),
                    factsFor(relation),
                    NOW,
                )
                val full = RoomViewerPolicy.decide(
                    room(status = status, startAt = startAt, currentParticipants = 8, maxCapacity = 8),
                    factsFor(relation),
                    NOW,
                )

                assertThat(full.blockReason).describedAs("$relation × $status").isEqualTo(roomy.blockReason)
            }
        }
    }

    private fun forEachCombination(assertion: (ViewerRelation, RoomStatus, RoomViewer) -> Unit) {
        ViewerRelation.entries.forEach { relation ->
            RoomStatus.entries.forEach { status ->
                val target = room(
                    status = status,
                    startAt = if (status == RoomStatus.RECRUITING) FUTURE else PAST,
                )
                assertion(relation, status, RoomViewerPolicy.decide(target, factsFor(relation), NOW))
            }
        }
    }

    // 관계 하나를 만들어 내는 최소 사실 묶음. 판정이 아니라 픽스처다.
    private fun factsFor(relation: ViewerRelation): ViewerFacts? = when (relation) {
        ViewerRelation.ANONYMOUS -> null
        ViewerRelation.NONE -> facts()
        ViewerRelation.APPLIED -> facts(application = RoomApplicationStatus.PENDING)
        ViewerRelation.WITHDRAWN -> facts(application = RoomApplicationStatus.WITHDRAWN)
        ViewerRelation.APPLICATION_CLOSED -> facts(application = RoomApplicationStatus.ROOM_CANCELED)
        ViewerRelation.REJECTED -> facts(application = RoomApplicationStatus.REJECTED)
        ViewerRelation.REMOVED -> facts(removed = true, application = RoomApplicationStatus.ACCEPTED)
        ViewerRelation.PARTICIPANT -> participant()
        ViewerRelation.HOST -> host()
    }

    private fun host(): ViewerFacts = facts(host = true, participating = true)

    private fun participant(): ViewerFacts = facts(
        participating = true,
        application = RoomApplicationStatus.ACCEPTED,
    )

    private fun facts(
        host: Boolean = false,
        participating: Boolean = false,
        removed: Boolean = false,
        application: RoomApplicationStatus? = null,
        active: Boolean = true,
        slotAvailable: Boolean = true,
        quotaAvailable: Boolean = true,
    ): ViewerFacts = ViewerFacts(
        room = ViewerRoomFacts(
            host = host,
            participating = participating,
            removed = removed,
            latestApplication = application,
        ),
        member = ViewerMemberFacts(
            active = active,
            participationSlotAvailable = slotAvailable,
            applicationQuotaAvailable = quotaAvailable,
        ),
    )

    private fun room(
        status: RoomStatus = RoomStatus.RECRUITING,
        startAt: LocalDateTime = FUTURE,
        currentParticipants: Int = 3,
        maxCapacity: Int = 8,
    ): RoomApplicability = RoomApplicability(
        status = status,
        startAt = startAt,
        currentParticipants = currentParticipants,
        maxCapacity = maxCapacity,
    )

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 13, 12, 0)
        val FUTURE: LocalDateTime = NOW.plusDays(1)
        val PAST: LocalDateTime = NOW.minusHours(1)
    }
}
