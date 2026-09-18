package io.plady.moimyeon.worker

import io.plady.moimyeon.WorkerApplication
import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor

@Tag("context")
@ActiveProfiles("test")
@SpringBootTest(
    classes = [WorkerApplication::class],
    properties = [
        "room.auto-complete.enabled=true",
        "notification.email.ses.from-address=worker-test@example.invalid",
        "notification.email.gmail.from-address=worker-test@example.invalid",
        "notification.web-push.fcm.project-id=worker-test",
        "notification.web-push.fcm.action-base-url=https://example.invalid",
    ],
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class WorkerContextTest
