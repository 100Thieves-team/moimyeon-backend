package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomCount
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 탐색 목록의 복합 조회 + 조립. 단건 조회의 RoomFinder 와 책임이 다르다.
// 다른 개념(회사·공고·직무·지역)은 보지 않는다 — 회사 → 공고 id 변환은 호출자가 끝내서 넘기고,
// 표시명 조립은 Facade 가 한다.
@Component
class RoomSearchReader(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val clock: Clock,
) {
    // 목록·전체 건수·표시용 집계를 한 스냅샷으로 읽는다. 쿼리 수는 룸 수와 무관한 상수 4개다.
    @Transactional(readOnly = true)
    fun search(
        condition: RoomSearchCondition,
        jobPostingIds: List<Long>?,
        sort: RoomSortOrder,
        cursor: RoomCursor?,
        size: Int,
    ): RoomCardPage {
        val now = LocalDateTime.now(clock)

        // 다음 페이지 유무는 한 건 더 읽어 판정한다. "꽉 찼으면 다음이 있다"고 추측하면
        // 정확히 size 건으로 끝날 때 빈 페이지가 한 번 더 나간다.
        val rows = findRows(condition, jobPostingIds, sort, cursor, now, limit = size + 1)
        val page = rows.take(size)
        val nextCursor = if (rows.size > size) page.last().toCursor(sort) else null

        val roomIds = page.map { it.id }
        val participants = countsByRoomId(participationRepository.countActiveByRoomIds(roomIds))
        val pending = countsByRoomId(roomApplicationRepository.countPendingByRoomIds(roomIds))

        return RoomCardPage(
            cards = page.map {
                RoomCard(
                    room = RoomMapper.toDomain(it),
                    // 집계에 없는 룸은 그 값이 0인 룸이다. 여기서 채우지 않으면 조립에서 키가 없어 터진다.
                    currentParticipants = participants[it.id] ?: 0,
                    pendingApplications = pending[it.id] ?: 0,
                )
            },
            nextCursor = nextCursor,
            totalCount = countTargets(condition, jobPostingIds, now),
        )
    }

    private fun findRows(
        condition: RoomSearchCondition,
        jobPostingIds: List<Long>?,
        sort: RoomSortOrder,
        cursor: RoomCursor?,
        now: LocalDateTime,
        limit: Int,
    ): List<RoomEntity> {
        val pageable = PageRequest.of(0, limit)
        return when (sort) {
            RoomSortOrder.SCHEDULE -> roomRepository.searchBySchedule(
                now, jobPostingIds, condition.jobRoleId, condition.interviewStage, condition.meetingType,
                condition.sigunguId, condition.startFrom, condition.startTo, condition.availableOnly,
                cursor?.sortValue, cursor?.id, pageable,
            )

            RoomSortOrder.RECENT -> roomRepository.searchByRecent(
                now, jobPostingIds, condition.jobRoleId, condition.interviewStage, condition.meetingType,
                condition.sigunguId, condition.startFrom, condition.startTo, condition.availableOnly,
                cursor?.sortValue, cursor?.id, pageable,
            )
        }
    }

    // 전체 건수는 커서를 보지 않는다 — 커서는 순회 위치일 뿐 조회 대상이 아니다.
    private fun countTargets(condition: RoomSearchCondition, jobPostingIds: List<Long>?, now: LocalDateTime): Long = roomRepository.countSearchTargets(
        now, jobPostingIds, condition.jobRoleId, condition.interviewStage, condition.meetingType,
        condition.sigunguId, condition.startFrom, condition.startTo, condition.availableOnly,
    )

    private fun countsByRoomId(rows: List<RoomCount>): Map<UUID, Int> = rows.associate { it.roomId to it.count.toInt() }

    private fun RoomEntity.toCursor(sort: RoomSortOrder): RoomCursor = RoomCursor(
        sortValue = when (sort) {
            RoomSortOrder.SCHEDULE -> startAt
            RoomSortOrder.RECENT -> createdAt
        },
        id = id,
    )
}
