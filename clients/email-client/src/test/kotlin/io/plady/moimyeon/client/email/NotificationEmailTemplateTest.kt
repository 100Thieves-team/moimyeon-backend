package io.plady.moimyeon.client.email

import io.plady.moimyeon.worker.notification.delivery.NotificationContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationEmailTemplateTest {
    private val template = NotificationEmailTemplate()

    @Test
    fun `알림 제목을 메일 제목으로 쓴다`() {
        val message = template.render(TO, content())

        assertThat(message.to).isEqualTo(TO)
        assertThat(message.subject).isEqualTo("새 참가 신청이 왔어요")
    }

    @Test
    fun `알림 제목과 본문을 HTML 본문에 담는다`() {
        val message = template.render(TO, content())

        assertThat(message.htmlBody)
            .contains("새 참가 신청이 왔어요")
            .contains("백엔드 모의면접 스터디")
    }

    @Test
    fun `이동 링크를 버튼과 대체 주소에 담는다`() {
        val message = template.render(TO, content())

        assertThat(message.htmlBody)
            .contains("href=\"$ACTION_URL\"")
            .contains("자세히 보기")
    }

    @Test
    fun `이동 링크가 없으면 버튼을 넣지 않는다`() {
        val message = template.render(TO, content(actionUrl = null))

        assertThat(message.htmlBody)
            .contains("백엔드 모의면접 스터디")
            .doesNotContain("자세히 보기")
            .doesNotContain("href=")
    }

    @Test
    fun `제목과 본문의 HTML 특수문자를 이스케이프한다`() {
        val message = template.render(
            TO,
            content(
                title = "<script>alert(1)</script>",
                body = "'<b>Kotlin</b> & \"주니어\" 백엔드 라운드'에 참가 신청이 들어왔어요.",
            ),
        )

        assertThat(message.htmlBody)
            .contains("&lt;b&gt;Kotlin&lt;/b&gt;")
            .contains("&quot;주니어&quot;")
            .contains("&amp;")
            .doesNotContain("<b>Kotlin")
            .doesNotContain("<script>")
    }

    // 조각 파일의 href 는 쌍따옴표로 감싸므로 속성을 빠져나갈 문자를 막아야 한다.
    @Test
    fun `이동 링크의 HTML 특수문자를 이스케이프한다`() {
        val message = template.render(TO, content(actionUrl = "https://front.test/rooms?a=1&b=\"x\""))

        assertThat(message.htmlBody)
            .contains("href=\"https://front.test/rooms?a=1&amp;b=&quot;x&quot;\"")
            .doesNotContain("b=\"x\"")
    }

    @Test
    fun `이동 링크에는 줄바꿈 변환을 적용하지 않는다`() {
        val message = template.render(TO, content(body = "첫 줄\n둘째 줄"))

        assertThat(message.htmlBody)
            .contains("첫 줄<br>둘째 줄")
            .doesNotContain("$ACTION_URL<br>")
    }

    @Test
    fun `본문의 줄바꿈을 br로 바꾼다`() {
        val message = template.render(TO, content(body = "첫 줄\n둘째 줄"))

        assertThat(message.htmlBody).contains("첫 줄<br>둘째 줄")
    }

    @Test
    fun `본문을 받은 편지함 미리보기 한 줄로 넣는다`() {
        val message = template.render(TO, content(body = "첫 줄\n둘째 줄"))

        assertThat(message.htmlBody).contains("첫 줄 둘째 줄")
    }

    @Test
    fun `템플릿과 코드의 자리표시자 이름이 어긋나지 않는다`() {
        listOf(content(), content(actionUrl = null)).forEach {
            assertThat(template.render(TO, it).htmlBody).doesNotContain("{{")
        }
    }

    @Test
    fun `본문에 자리표시자처럼 보이는 글자가 있어도 치환하지 않는다`() {
        val message = template.render(
            TO,
            content(body = "'{{action}} 스터디'에 참가 신청이 들어왔어요.", actionUrl = null),
        )

        assertThat(message.htmlBody)
            .contains("{{action}} 스터디")
            .doesNotContain("자세히 보기")
            .doesNotContain("href=")
    }

    @Test
    fun `본문에 자리표시자처럼 보이는 글자가 있어도 버튼을 한 번만 넣는다`() {
        val message = template.render(TO, content(body = "'{{action}} 스터디'에 참가 신청이 들어왔어요."))

        assertThat(message.htmlBody).containsOnlyOnce("자세히 보기")
    }

    @Test
    fun `제목의 줄바꿈을 한 줄로 합친다`() {
        val message = template.render(TO, content(title = "새 참가 신청이\n왔어요"))

        assertThat(message.subject).isEqualTo("새 참가 신청이 왔어요")
        assertThat(message.htmlBody)
            .contains("새 참가 신청이 왔어요")
            .doesNotContain("새 참가 신청이<br>왔어요")
    }

    @Test
    fun `평문 대체 본문은 본문과 이동 링크를 빈 줄로 잇는다`() {
        val message = template.render(TO, content())

        assertThat(message.textBody).isEqualTo(
            "'백엔드 모의면접 스터디'에 참가 신청이 들어왔어요.\n\n$ACTION_URL",
        )
    }

    @Test
    fun `이동 링크가 없으면 평문 대체 본문은 본문만 담는다`() {
        val message = template.render(TO, content(actionUrl = null))

        assertThat(message.textBody).isEqualTo("'백엔드 모의면접 스터디'에 참가 신청이 들어왔어요.")
    }

    @Test
    fun `평문 대체 본문은 본문을 이스케이프하지 않고 레이아웃 HTML도 섞지 않는다`() {
        val message = template.render(TO, content(body = "Kotlin & 백엔드"))

        assertThat(message.textBody).isEqualTo("Kotlin & 백엔드\n\n$ACTION_URL")
    }

    private fun content(
        title: String = "새 참가 신청이 왔어요",
        body: String = "'백엔드 모의면접 스터디'에 참가 신청이 들어왔어요.",
        actionUrl: String? = ACTION_URL,
    ) = NotificationContent(
        title = title,
        body = body,
        actionUrl = actionUrl,
    )
}

private const val TO = "member@example.com"
private const val ACTION_URL = "https://moimyeon.plady.io/interviews/e89da2d2-03ee-4d01-8d0d-e9badde0e74d"
