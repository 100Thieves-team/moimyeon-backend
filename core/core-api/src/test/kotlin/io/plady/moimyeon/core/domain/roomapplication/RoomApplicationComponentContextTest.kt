package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.ContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext

class RoomApplicationComponentContextTest(
    private val applicationContext: ApplicationContext,
) : ContextTest() {
    @Test
    fun `기존 룸 컴포넌트와 참가 신청 컴포넌트가 역할 이름으로 함께 등록된다`() {
        assertThat(applicationContext.beanDefinitionNames).contains(
            "roomApplicationService",
            "roomApplicationManager",
            "roomApplicationSubmissionService",
            "roomApplicationSubmissionFinder",
            "roomApplicationSubmissionManager",
        )
        assertThat(applicationContext.beanDefinitionNames).doesNotContain(
            "applicantRoomApplicationService",
            "applicantRoomApplicationFinder",
            "applicantRoomApplicationManager",
        )
    }
}
