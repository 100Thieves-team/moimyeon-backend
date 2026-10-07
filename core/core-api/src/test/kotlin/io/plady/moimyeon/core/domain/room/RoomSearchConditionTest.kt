package io.plady.moimyeon.core.domain.room

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoomSearchConditionTest {
    @Test
    fun `회사도 공고도 지정하지 않으면 공고로 좁히지 않는다`() {
        assertThat(RoomSearchCondition.EMPTY.resolveJobPostingTargets(null)).isNull()
    }

    @Test
    fun `공고만 지정하면 그 공고 하나로 좁힌다`() {
        val condition = RoomSearchCondition.EMPTY.copy(jobPostingId = 11L)

        assertThat(condition.resolveJobPostingTargets(null)).containsExactly(11L)
    }

    @Test
    fun `회사만 지정하면 그 회사의 공고 전체로 좁힌다`() {
        val condition = RoomSearchCondition.EMPTY.copy(companyId = 1L)

        assertThat(condition.resolveJobPostingTargets(listOf(11L, 22L))).containsExactly(11L, 22L)
    }

    @Test
    fun `회사와 공고를 함께 지정하면 둘을 모두 만족하는 공고만 남는다`() {
        val condition = RoomSearchCondition.EMPTY.copy(companyId = 1L, jobPostingId = 11L)

        assertThat(condition.resolveJobPostingTargets(listOf(11L, 22L))).containsExactly(11L)
    }

    // 빈 목록은 "조건 없음"과 정반대의 뜻이다. null 로 뭉뚱그리면 필터가 조용히 풀려 전체 목록이 나간다.
    @Test
    fun `그 회사의 공고가 아닌 공고를 함께 지정하면 좁힌 결과가 없다`() {
        val condition = RoomSearchCondition.EMPTY.copy(companyId = 1L, jobPostingId = 99L)

        assertThat(condition.resolveJobPostingTargets(listOf(11L, 22L))).isEmpty()
    }

    @Test
    fun `공고가 하나도 없는 회사로 좁히면 좁힌 결과가 없다`() {
        val condition = RoomSearchCondition.EMPTY.copy(companyId = 1L)

        assertThat(condition.resolveJobPostingTargets(emptyList())).isEmpty()
    }
}
