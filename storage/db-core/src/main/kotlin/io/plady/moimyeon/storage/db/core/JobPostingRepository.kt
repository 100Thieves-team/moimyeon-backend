package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JobPostingRepository : JpaRepository<JobPostingEntity, Long> {
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
