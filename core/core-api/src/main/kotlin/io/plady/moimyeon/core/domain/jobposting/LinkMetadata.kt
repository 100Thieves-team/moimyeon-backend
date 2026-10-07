package io.plady.moimyeon.core.domain.jobposting

// 링크의 OG 태그로 만든 공고 미리보기(「룸 생성」 §4.1). 서버가 대신 fetch 해 채운다(브라우저는 CORS 로 외부 페이지를 못 읽는다).
// fetch 실패·OG 없음이면 postingName 이 null 이고, 그때 사용자는 공고명을 직접 입력한다(깨진 링크 폴백).
// sourceUrl 은 og:url 이 있으면 그 값을, 없으면 요청 url 을 그대로 쓴다(항상 채워진다).
data class LinkMetadata(
    val postingName: String?,
    val imageUrl: String?,
    val description: String?,
    val sourceUrl: String,
)
