package io.plady.moimyeon.core.qa

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * QA 회원 생성 설정.
 *
 * emailTemplate 의 `{key}` 자리에 회원마다 다른 키가 들어간다. 알림 메일을 실제로 받아 확인하려면
 * 받을 수 있는 메일함 주소로 둔다(예: `moimyeon.qa+qa-{key}@gmail.com`). QA 회원 판별은 OAuth 식별자
 * 접두어로도 하므로 주소를 바꿔도 조회·정리는 그대로다.
 */
@ConfigurationProperties("qa.member")
data class QaMemberProperties(
    val emailTemplate: String = DEFAULT_EMAIL_TEMPLATE,
) {
    init {
        require(KEY in emailTemplate) { "qa.member.email-template 에 $KEY 가 있어야 합니다: $emailTemplate" }
    }

    fun emailOf(key: String): String = emailTemplate.replace(KEY, key)

    companion object {
        const val KEY = "{key}"
        const val DEFAULT_EMAIL_TEMPLATE = "${QaMemberCreator.EMAIL_LOCAL_PREFIX}$KEY@${QaMemberCreator.EMAIL_DOMAIN}"
    }
}
