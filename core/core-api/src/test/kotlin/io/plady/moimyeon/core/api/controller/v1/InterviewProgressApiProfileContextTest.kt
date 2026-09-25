package io.plady.moimyeon.core.api.controller.v1

import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.facade.QuestionCommentFacade
import io.plady.moimyeon.core.api.facade.RoomProgressFacade
import io.plady.moimyeon.core.api.facade.RoundFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.question.QuestionCommentService
import io.plady.moimyeon.core.domain.question.QuestionProgressService
import io.plady.moimyeon.core.domain.roundfeedback.RoundFeedbackService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.RequestMapping
import java.lang.reflect.Method
import java.util.UUID
import java.util.function.Supplier

@Tag("context")
class InterviewProgressApiProfileContextTest {
    private val contextRunner = ApplicationContextRunner()
        .withBean(QuestionCommentService::class.java, Supplier { mockk(relaxed = true) })
        .withBean(QuestionCommentFacade::class.java, Supplier { mockk(relaxed = true) })
        .withBean(QuestionProgressService::class.java, Supplier { mockk(relaxed = true) })
        .withBean(RoomProgressFacade::class.java, Supplier { mockk(relaxed = true) })
        .withBean(RoundFacade::class.java, Supplier { mockk(relaxed = true) })
        .withBean(RoundFeedbackService::class.java, Supplier { mockk(relaxed = true) })
        .withUserConfiguration(
            ProgressRailController::class.java,
            QuestionCommentController::class.java,
            QuestionProgressController::class.java,
            RoundController::class.java,
            RoundFeedbackController::class.java,
        )

    @Test
    fun `dev가 아닌 프로파일에서는 MVP 제외 API Controller를 등록하지 않는다`() {
        listOf("local", "local-dev", "staging", "live", "test").forEach { profile ->
            contextRunner.withPropertyValues("spring.profiles.active=$profile").run { context ->
                gatedControllerClasses.forEach { assertThat(context).doesNotHaveBean(it) }
            }
        }
    }

    @Test
    fun `dev 프로파일에서는 MVP 제외 API Controller를 등록한다`() {
        contextRunner
            .withPropertyValues("spring.profiles.active=dev")
            .run { context ->
                gatedControllerClasses.forEach { assertThat(context).hasSingleBean(it) }
            }
    }

    @Test
    fun `조건부 Controller는 MVP 제외 API 15개만 보존한다`() {
        assertThat(gatedControllerClasses.flatMap(::mappings))
            .containsExactlyInAnyOrder(
                "GET /v1/progress-rails",
                "GET /v1/question-comments",
                "POST /v1/question-comments",
                "PATCH /v1/question-comment-types/{commentId}",
                "PATCH /v1/question-comments/{commentId}",
                "DELETE /v1/question-comments/{commentId}",
                "POST /v1/questions",
                "POST /v1/follow-up-questions",
                "PATCH /v1/questions/{questionId}",
                "GET /v1/rounds",
                "GET /v1/question-records/me",
                "POST /v1/final-feedbacks",
                "PUT /v1/self-feedbacks",
                "GET /v1/round-feedbacks",
                "PUT /v1/feedback-disclosures/{feedbackId}",
            )
    }

    @Test
    fun `조건부 API의 모든 핸들러는 로그인 회원을 요구한다`() {
        gatedControllerClasses
            .flatMap { controllerClass -> controllerClass.declaredMethods.filter(::isMapped) }
            .forEach { method ->
                assertThat(
                    method.parameters.any { parameter ->
                        parameter.type == CurrentMember::class.java &&
                            parameter.isAnnotationPresent(LoginMember::class.java)
                    },
                ).describedAs(method.toGenericString()).isTrue()
            }
    }

    @Test
    fun `조건부 Controller의 대표 API는 미인증 요청을 거부한다`() {
        val mockMvc = MockMvcBuilders.standaloneSetup(
            ProgressRailController(mockk(relaxed = true)),
            QuestionCommentController(mockk(relaxed = true), mockk(relaxed = true)),
            QuestionProgressController(mockk(relaxed = true)),
            RoundController(mockk(relaxed = true)),
            RoundFeedbackController(mockk(relaxed = true)),
        )
            .setCustomArgumentResolvers(LoginMemberArgumentResolver())
            .setControllerAdvice(ApiControllerAdvice())
            .build()
        val roomId = UUID.randomUUID().toString()
        val intervieweeMemberId = UUID.randomUUID().toString()

        listOf(
            get("/v1/progress-rails").queryParam("roomId", roomId),
            get("/v1/question-comments")
                .queryParam("roomId", roomId)
                .queryParam("intervieweeMemberId", intervieweeMemberId)
                .queryParam("questionId", "1"),
            post("/v1/questions"),
            get("/v1/rounds")
                .queryParam("roomId", roomId)
                .queryParam("intervieweeMemberId", intervieweeMemberId),
            get("/v1/question-records/me")
                .queryParam("roomId", roomId)
                .queryParam("intervieweeMemberId", intervieweeMemberId),
        ).forEach { request ->
            mockMvc.perform(request).andExpect(status().isUnauthorized)
        }
    }

    private fun mappings(controllerClass: Class<*>): List<String> = controllerClass.declaredMethods
        .mapNotNull { method ->
            val mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
                ?: return@mapNotNull null
            val path = mapping.path.firstOrNull() ?: mapping.value.first()
            "${mapping.method.single().name} $path"
        }

    private fun isMapped(method: Method): Boolean = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java) != null

    private companion object {
        val gatedControllerClasses = listOf(
            ProgressRailController::class.java,
            QuestionCommentController::class.java,
            QuestionProgressController::class.java,
            RoundController::class.java,
            RoundFeedbackController::class.java,
        )
    }
}
