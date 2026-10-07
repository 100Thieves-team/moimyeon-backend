package io.plady.moimyeon.worker

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WorkerModuleBoundaryTest {
    @Test
    fun `워커 런타임은 core-api 애플리케이션을 포함하지 않는다`() {
        assertThatThrownBy {
            Class.forName("io.plady.moimyeon.CoreApiApplication")
        }.isInstanceOf(ClassNotFoundException::class.java)
    }
}
