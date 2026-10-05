package io.plady.moimyeon.client.email

import io.plady.moimyeon.worker.notification.delivery.NotificationContent

internal class NotificationEmailTemplate {
    private val layout = loadTemplate(LAYOUT_PATH)
    private val actionFragment = loadTemplate(ACTION_FRAGMENT_PATH)

    fun render(
        to: String,
        content: NotificationContent,
    ): EmailMessage = EmailMessage(
        to = to,
        subject = content.title.singleLine(),
        htmlBody = renderHtml(content),
        textBody = renderText(content),
    )

    private fun renderHtml(content: NotificationContent): String = layout.fill(
        // 제목은 h1 과 title 태그에 함께 들어가므로 줄바꿈을 한 줄로 합친다.
        inline = mapOf(
            "preheader" to content.body.singleLine(),
            "title" to content.title.singleLine(),
        ),
        paragraph = mapOf("body" to content.body),
        html = mapOf("action" to renderAction(content.actionUrl)),
    )

    private fun renderAction(actionUrl: String?): String = actionUrl?.let { actionFragment.fill(inline = mapOf("actionUrl" to it)) } ?: ""

    // 평문 수신자가 보는 메일은 HTML 템플릿 도입 전과 같다.
    private fun renderText(content: NotificationContent): String = listOfNotNull(content.body, content.actionUrl).joinToString("\n\n")

    // 계약: 템플릿을 한 번만 훑으므로 채워 넣은 값이 다시 자리표시자로 읽히지 않는다.
    // 룸 제목 같은 사용자 입력이 본문 문구에 그대로 들어오기 때문에 필요한 성질이다.
    // 슬롯은 셋 중 하나로 다룬다 — inline 은 이스케이프만(속성·한 줄 텍스트),
    // paragraph 는 이스케이프에 줄바꿈 변환까지, html 은 우리가 만든 조각이라 날것이다.
    // 템플릿에만 있는 이름은 즉시 실패시킨다 — 자리표시자 오타가 메일에 실려 나가지 않는다.
    private fun String.fill(
        inline: Map<String, String> = emptyMap(),
        paragraph: Map<String, String> = emptyMap(),
        html: Map<String, String> = emptyMap(),
    ): String = PLACEHOLDER.replace(this) { match ->
        val name = match.groupValues[1]
        inline[name]?.escapeHtml()
            ?: paragraph[name]?.escapeHtml()?.replace("\n", "<br>")
            ?: html[name]
            ?: error("알 수 없는 자리표시자: ${match.value}")
    }

    private fun String.escapeHtml(): String = buildString(length) {
        this@escapeHtml.forEach {
            when (it) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(it)
            }
        }
    }

    private fun String.singleLine(): String = replace(WHITESPACE, " ").trim()

    private fun loadTemplate(path: String): String = checkNotNull(NotificationEmailTemplate::class.java.getResourceAsStream(path)) {
        "메일 템플릿을 찾을 수 없습니다: $path"
    }.use { it.readBytes().decodeToString() }

    private companion object {
        const val LAYOUT_PATH = "/email/notification-email.html"
        const val ACTION_FRAGMENT_PATH = "/email/notification-email-action.html"
        val PLACEHOLDER = Regex("\\{\\{(\\w+)}}")
        val WHITESPACE = Regex("\\s+")
    }
}
