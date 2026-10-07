package io.plady.moimyeon.client.bedrock

internal object ResumePersonalInfoRedactor {
    fun redact(text: String): String {
        return listOf(
            LABELED_PERSONAL_INFO,
            EMAIL,
            KOREAN_PHONE_NUMBER,
            RESIDENT_REGISTRATION_NUMBER,
            KOREAN_ROAD_ADDRESS,
        ).fold(text) { redacted, pattern ->
            pattern.replace(redacted) { result ->
                if (result.groups.size > 1 && result.groups[1] != null) {
                    "${result.groups[1]?.value}$REDACTED"
                } else {
                    REDACTED
                }
            }
        }
    }
}

private const val REDACTED = "[REDACTED]"
private val LABELED_PERSONAL_INFO = Regex(
    pattern = "^(\\s*(?:이메일|email|전화(?:번호)?|휴대폰|mobile|주소|address|학번|student\\s*id)\\s*[:：]\\s*).+$",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)
private val EMAIL = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
private val KOREAN_PHONE_NUMBER = Regex(
    "(?<!\\d)(?:(?:\\+82\\s*[-.]?\\s*)(?:1[016789]|2|[3-8]\\d)|0(?:1[016789]|2|[3-8]\\d))" +
        "\\s*[-.]?\\s*\\d{3,4}\\s*[-.]?\\s*\\d{4}(?!\\d)",
)
private val RESIDENT_REGISTRATION_NUMBER = Regex("(?<!\\d)\\d{6}\\h*-?\\h*[1-8]\\d{6}(?!\\d)")
private val KOREAN_ROAD_ADDRESS = Regex(
    "(?:서울(?:특별시|시)?|부산(?:광역시|시)?|대구(?:광역시|시)?|인천(?:광역시|시)?|" +
        "광주(?:광역시|시)?|대전(?:광역시|시)?|울산(?:광역시|시)?|세종(?:특별자치시|시)?|" +
        "경기(?:도)?|강원(?:특별자치도|도)?|충청[남북]도|전라[남북]도|경상[남북]도|제주(?:특별자치도|도)?)" +
        "(?:\\s+[가-힣]+(?:시|군|구)){1,2}\\s+[가-힣0-9]+(?:로|길)\\s+\\d+(?:-\\d+)?",
)
