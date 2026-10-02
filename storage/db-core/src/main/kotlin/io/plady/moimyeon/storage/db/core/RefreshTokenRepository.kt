package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    fun deleteByExpiresAtBefore(now: LocalDateTime): Long

    // 회원 탈퇴: 그 회원의 모든 기기 세션을 끝낸다. 이미 폐기된 세션의 최초 폐기 시각은 덮어쓰지 않는다.
    // clearAutomatically 를 켜지 않는다 — 탈퇴 트랜잭션이 잡고 있는 회원 엔티티가 준영속이 되면 삭제가 사라진다.
    // updatedAt 은 @UpdateTimestamp 라 벌크에서 돌지 않아 set 절에 직접 넣는다.
    @Modifying(flushAutomatically = true)
    @Query(
        """
        update RefreshTokenEntity t
        set t.revokedAt = :now, t.updatedAt = :now
        where t.memberId = :memberId
          and t.revokedAt is null
        """,
    )
    fun revokeAllByMemberId(
        @Param("memberId") memberId: UUID,
        @Param("now") now: LocalDateTime,
    ): Int
}
