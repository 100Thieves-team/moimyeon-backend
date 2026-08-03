package io.plady.moimyeon.core.domain.jobposting

// 링크의 OG 메타데이터를 서버측에서 읽어온다(「룸 생성」 §4.1). 브라우저는 CORS 로 외부 채용 페이지를
// 직접 읽을 수 없어 서버가 대신 fetch 한다. 실패·차단·타임아웃·비HTML 은 예외가 아니라 빈 메타로 흡수한다 —
// 미리보기는 편의 기능이고, 실패하면 사용자가 공고명을 직접 입력해 생성으로 넘어가면 되기 때문이다.
interface OpenGraphClient {
    fun fetch(url: String): LinkMetadata
}
