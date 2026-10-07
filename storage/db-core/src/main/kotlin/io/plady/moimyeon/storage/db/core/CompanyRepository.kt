package io.plady.moimyeon.storage.db.core

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CompanyRepository : JpaRepository<CompanyEntity, Long> {
    // 통합 검색의 회사 매치(MOI-390). 정규화 값에 패턴 3개를 OR 로 걸어 한 번에 조회한다.
    // 접두 패턴('네이버%')이면 ix_company_name_normalized 를 range scan 으로 탄다.
    // 후보가 3개 미만이면 호출자가 같은 패턴을 반복해 넘긴다 — OR 이라 결과가 달라지지 않는다.
    // 짧은 이름이 앞에 오도록 정렬해, 가장 긴 후보를 고를 때 대표값을 먼저 보게 한다.
    @Query(
        """
        SELECT c FROM CompanyEntity c
        WHERE (c.nameNormalized LIKE :pattern1
            OR c.nameNormalized LIKE :pattern2
            OR c.nameNormalized LIKE :pattern3)
          AND c.verified = true
          AND c.deletedAt IS NULL
        ORDER BY LENGTH(c.nameNormalized), c.nameKr
        """,
    )
    fun searchByNormalizedPatterns(
        @Param("pattern1") pattern1: String,
        @Param("pattern2") pattern2: String,
        @Param("pattern3") pattern3: String,
        pageable: Pageable,
    ): List<CompanyEntity>

    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<CompanyEntity>

    fun countByIdInAndVerifiedTrueAndDeletedAtIsNull(ids: Collection<Long>): Long
}
