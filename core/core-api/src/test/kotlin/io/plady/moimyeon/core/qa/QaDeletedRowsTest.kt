package io.plady.moimyeon.core.qa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class QaDeletedRowsTest {
    private val countFields = QaDeletedRows::class.memberProperties.filter { it.returnType.classifier == Int::class }

    private fun allOnes(): QaDeletedRows {
        val ctor = QaDeletedRows::class.constructors.single()
        return ctor.callBy(ctor.parameters.associateWith { 1 })
    }

    @Test
    fun `plus 는 모든 테이블 건수를 항별로 더한다`() {
        val sum = allOnes() + allOnes()

        countFields.forEach { field ->
            assertThat(field.get(sum)).describedAs("${field.name} 이 plus 에서 빠졌다").isEqualTo(2)
        }
    }

    @Test
    fun `total 은 모든 테이블 건수의 합이다`() {
        assertThat(allOnes().total()).isEqualTo(countFields.size)
        assertThat(QaDeletedRows.NONE.total()).isZero()
    }

    @Test
    fun `로그 값은 모든 테이블을 다루고 0 인 테이블은 생략한다`() {
        val logged = allOnes().toLogValues()

        countFields.forEach { field ->
            assertThat(logged).describedAs("${field.name} 이 로그에서 빠졌다").contains("${field.name}=1")
        }
        assertThat(QaDeletedRows(rooms = 1).toLogValues()).isEqualTo("rooms=1")
        assertThat(QaDeletedRows.NONE.toLogValues()).isEqualTo("total=0")
    }
}
