package io.plady.moimyeon.core.domain.jobposting

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SearchKeywordTest {
    @Test
    fun `법인격과 공백을 제거해 정규화한다`() {
        assertThat(SearchKeyword.of("주식회사 카카오페이증권").normalized).isEqualTo("카카오페이증권")
        assertThat(SearchKeyword.of("(주)네이버").normalized).isEqualTo("네이버")
        assertThat(SearchKeyword.of("네이 버").normalized).isEqualTo("네이버")
    }

    @Test
    fun `회사 후보는 앞 토큰부터 누적하며 상한에서 잘린다`() {
        assertThat(SearchKeyword.of("네이버 백엔드").companyPrefixCandidates())
            .containsExactly("네이버", "네이버백엔드")

        assertThat(SearchKeyword.of("네이버 파이낸셜 클라우드 백엔드").companyPrefixCandidates())
            .containsExactly("네이버", "네이버파이낸셜", "네이버파이낸셜클라우드")
    }

    @Test
    fun `잔여 검색어는 정규화하지 않고 원본 공백을 유지한다`() {
        val keyword = SearchKeyword.of("네이버 프론트엔드 개발자")

        // 정규화한 잔여로 title 을 찾으면 '프론트엔드개발자' 가 되어 실제 공고명과 어긋난다
        assertThat(keyword.remainderAfter(1)).isEqualTo("프론트엔드 개발자")
        assertThat(keyword.remainderAfter(0)).isEqualTo("네이버 프론트엔드 개발자")
        assertThat(keyword.remainderAfter(3)).isEmpty()
    }
}
