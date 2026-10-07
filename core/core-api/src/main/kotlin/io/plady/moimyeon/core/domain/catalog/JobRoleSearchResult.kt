package io.plady.moimyeon.core.domain.catalog

// 직무 검색 결과 한 건(「룸 생성」 §4.1). 직무는 공고와 독립한 평면 카탈로그이며, 상위 직군을 함께 실어 준다.
data class JobRoleSearchResult(
    val id: Long,
    val code: String,
    val displayName: String,
    val groupCode: String,
    val groupDisplayName: String,
)
