package io.plady.moimyeon.core.qa

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class QaDataConditionTest {
    @Test
    fun `마커 판정은 대소문자를 무시한다`() {
        assertThat(QaDataCondition.isQaData("[QA] 스모크")).isTrue()
        assertThat(QaDataCondition.isQaData("[qa] 스모크")).isTrue()
        assertThat(QaDataCondition.isQaData("[Qa]")).isTrue()
    }

    @Test
    fun `마커로 시작하지 않으면 QA 데이터가 아니다`() {
        assertThat(QaDataCondition.isQaData("면접 스터디 [QA]")).isFalse()
        assertThat(QaDataCondition.isQaData(" [QA] 앞 공백")).isFalse()
        assertThat(QaDataCondition.isQaData("")).isFalse()
    }

    @Test
    fun `마커 없는 접두로는 조건을 만들 수 없다`() {
        assertThatThrownBy { QaDataCondition(prefix = "면접", hostMemberId = null) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
