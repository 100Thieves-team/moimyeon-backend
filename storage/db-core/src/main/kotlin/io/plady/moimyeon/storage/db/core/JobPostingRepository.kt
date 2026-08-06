package io.plady.moimyeon.storage.db.core

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface JobPostingRepository : JpaRepository<JobPostingEntity, Long> {
    // 통합 검색 rank 0 — 회사명이 매치된 회사들의 공고(MOI-390).
    // 잔여 검색어는 양방향으로 본다. 사용자가 공고명보다 길게 쳐도(오버타이핑) 결과가 사라지지 않아야 한다.
    // 잔여가 없으면 호출자가 titlePattern 에 '%' 를 넘겨 그 회사의 공고 전체가 된다.
    @Query(
        """
        SELECT p FROM JobPostingEntity p
        WHERE p.companyId IN :companyIds
          AND p.isOpen = true
          AND p.deletedAt IS NULL
          AND (p.title LIKE :titlePattern OR :remainder LIKE CONCAT('%', p.title, '%'))
        ORDER BY p.postedAt DESC, p.id DESC
        """,
    )
    fun searchByCompanyIds(
        @Param("companyIds") companyIds: Collection<Long>,
        @Param("titlePattern") titlePattern: String,
        @Param("remainder") remainder: String,
        pageable: Pageable,
    ): List<JobPostingEntity>

    // 통합 검색 rank 1 — 공고명 매치 폴백(MOI-390).
    // 토큰을 AND 로 걸어 어순과 무관하게 찾는다. 없는 토큰 자리에는 호출자가 '%' 를 넘긴다.
    // 절 개수가 고정이라 토큰 상한을 넘는 입력은 무시되는데, 결과가 넓어지는 방향이라 안전하다.
    @Query(
        """
        SELECT p FROM JobPostingEntity p
        WHERE p.companyId IS NOT NULL
          AND p.isOpen = true
          AND p.deletedAt IS NULL
          AND p.title LIKE :token1
          AND p.title LIKE :token2
          AND p.title LIKE :token3
        ORDER BY p.postedAt DESC, p.id DESC
        """,
    )
    fun searchByTitleTokens(
        @Param("token1") token1: String,
        @Param("token2") token2: String,
        @Param("token3") token3: String,
        pageable: Pageable,
    ): List<JobPostingEntity>

    // 회사에 속한 활성(is_open) 공고를 공고명 부분 일치로 검색해 최신순 최대 20건 반환한다.
    // verified 는 필터하지 않는다 — 룸 생성 목록에는 미검증(링크 생성분)도 노출하고, 탐색 필터에서만 숨긴다(BE-03).
    fun findTop20ByCompanyIdAndTitleContainingAndIsOpenTrueAndDeletedAtIsNullOrderByPostedAtDesc(
        companyId: Long,
        title: String,
    ): List<JobPostingEntity>

    // 회사-공고 소속 관계 검증(job_posting.company_id) + 신규 룸 사용 가능 여부(활성·미폐기). MOI-328 룸 생성에서 사용.
    fun existsByIdAndCompanyIdAndIsOpenTrueAndDeletedAtIsNull(id: Long, companyId: Long): Boolean

    // 링크 즉시 생성(BE-03)의 멱등키 조회. 앱이 URL 로 발급한 source_uid 가 이미 있으면 그 공고를 재사용한다.
    fun findBySourceUidAndDeletedAtIsNull(sourceUid: String): JobPostingEntity?

    // 생성 직후 응답을 저장된 값으로 재조립하기 위한 단건 조회(폐기분 제외).
    fun findByIdAndDeletedAtIsNull(id: Long): JobPostingEntity?
}
