package io.plady.moimyeon.core.domain.catalog

// 화면에 그대로 쓰는 지역 표시명. 시도 약칭 + 시군구명("서울 강남구").
// 시도나 시군구가 폐기되면 만들 수 없고, 그런 룸은 지역 없이 내려간다.
data class RegionLabel(
    val sigunguId: Long,
    val label: String,
)
