package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.qa.QaDeletedRows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class QaDeletedResponseTest {
    @Test
    fun `응답은 삭제 건수의 모든 테이블 필드를 같은 이름으로 싣고 total 을 더한다`() {
        val rowFields = QaDeletedRows::class.memberProperties.filter { it.returnType.classifier == Int::class }.map { it.name }.toSet()
        val responseFields = QaDeletedRowsResponse::class.memberProperties.map { it.name }.toSet()

        assertThat(responseFields).isEqualTo(rowFields + "total")
    }

    @Test
    fun `응답의 각 필드는 같은 이름의 삭제 건수를 그대로 옮긴다`() {
        val ctor = QaDeletedRows::class.constructors.single()
        val rows = ctor.callBy(ctor.parameters.withIndex().associate { (i, p) -> p to i + 1 })
        val rowValues = QaDeletedRows::class.memberProperties.filter { it.returnType.classifier == Int::class }.associate { it.name to it.get(rows) }

        val response = QaDeletedRowsResponse.from(rows)

        QaDeletedRowsResponse::class.memberProperties.filter { it.name != "total" }.forEach { field ->
            assertThat(field.get(response)).describedAs(field.name).isEqualTo(rowValues.getValue(field.name))
        }
        assertThat(response.total).isEqualTo(rows.total())
    }
}
