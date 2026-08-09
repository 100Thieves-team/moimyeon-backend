package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {
    // 수락 시 룸 행에 쓰기 잠금을 걸어 동시 수락을 직렬화한다(「룸 참여」 §4.4 마지막 자리 1건만 성공).
    // 잠금을 잡은 뒤 현재 인원을 세고 정원을 확정하므로, 경쟁하는 수락 트랜잭션은 순서대로만 통과한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): RoomEntity?

    // 탐색 목록 — 일정 빠른 순(MOI-383 §4.3). 정렬 방향이 달라 ORDER BY 를 파라미터로 줄 수 없으므로
    // 최근 생성순과 쿼리를 따로 둔다. 기본 제외 규칙(아래 3줄)은 세 쿼리가 글자 그대로 같아야 한다.
    //
    // 커서 비교가 (start_at, id) 복합인 이유: 정렬 키만 비교하면 같은 start_at 이 페이지 경계에 걸릴 때
    // '>' 는 동점을 통째로 건너뛰고 '>=' 는 통째로 다시 준다. 표준 SQL 의 행 값 비교
    // (start_at, id) > (:s, :id) 를 JPQL 이 지원하지 않아 OR 로 풀어 쓴 것이며 의미는 같다.
    // ORDER BY 도 비교와 같은 (start_at, id) 여야 한다 — 다르면 동점 구간 내부 순서가 흔들린다.
    @Query(
        """
        SELECT r FROM RoomEntity r
        WHERE r.status = io.plady.moimyeon.core.enums.RoomStatus.RECRUITING
          AND r.deletedAt IS NULL
          AND r.startAt > :now
          AND (:cursorStartAt IS NULL
               OR r.startAt > :cursorStartAt
               OR (r.startAt = :cursorStartAt AND r.id > :cursorId))
        ORDER BY r.startAt ASC, r.id ASC
        """,
    )
    fun searchBySchedule(
        @Param("now") now: LocalDateTime,
        @Param("cursorStartAt") cursorStartAt: LocalDateTime?,
        @Param("cursorId") cursorId: UUID?,
        pageable: Pageable,
    ): List<RoomEntity>

    // 탐색 목록 — 최근 생성순. 정렬이 내림차순이라 커서 비교도 '<' 로 뒤집힌다.
    @Query(
        """
        SELECT r FROM RoomEntity r
        WHERE r.status = io.plady.moimyeon.core.enums.RoomStatus.RECRUITING
          AND r.deletedAt IS NULL
          AND r.startAt > :now
          AND (:cursorCreatedAt IS NULL
               OR r.createdAt < :cursorCreatedAt
               OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId))
        ORDER BY r.createdAt DESC, r.id DESC
        """,
    )
    fun searchByRecent(
        @Param("now") now: LocalDateTime,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: UUID?,
        pageable: Pageable,
    ): List<RoomEntity>

    // 전체 건수(MOI-383 §4.1). 커서는 순회 위치일 뿐 조회 대상을 바꾸지 않으므로 여기서는 보지 않는다.
    @Query(
        """
        SELECT COUNT(r) FROM RoomEntity r
        WHERE r.status = io.plady.moimyeon.core.enums.RoomStatus.RECRUITING
          AND r.deletedAt IS NULL
          AND r.startAt > :now
        """,
    )
    fun countSearchTargets(@Param("now") now: LocalDateTime): Long
}
