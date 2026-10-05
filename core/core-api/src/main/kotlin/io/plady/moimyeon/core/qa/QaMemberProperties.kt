package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.storage.db.core.qa.QaEmailPattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

/**
 * QA 회원 생성 설정.
 *
 * emailTemplate 의 `{key}` 자리에 회원마다 다른 키가 들어간다. 알림 메일을 실제로 받아 확인하려면
 * 받을 수 있는 메일함 주소로 둔다(예: `moimyeon.qa+qa-{key}@gmail.com`).
 * QA 회원은 소셜 식별자 접두와 이메일 형식을 둘 다 만족해야 한다. 이메일 형식은 이 템플릿과 예전 기본 도메인 둘 중 하나면 된다.
 * 템플릿을 바꾸기 전에 만든 QA 회원도 계속 조회·정리할 수 있게 하기 위해서다.
 * 다른 QA 빈과 같이 dev 계열에서만 만든다. 이 설정이 잘못돼도 staging·live 기동은 막지 않는다.
 */
@ConfigurationProperties("qa.member")
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
data class QaMemberProperties(
    val emailTemplate: String = DEFAULT_EMAIL_TEMPLATE,
) {
    init {
        require(emailTemplate.split(KEY).size == 2) { "qa.member.email-template 에 $KEY 가 한 번 있어야 합니다: $emailTemplate" }
        Email(emailOf(SAMPLE_KEY))
    }

    fun emailOf(key: String): String = emailTemplate.replace(KEY, key)

    fun emailPatterns(): List<QaEmailPattern> {
        val (prefix, suffix) = emailTemplate.split(KEY)
        return listOf(QaEmailPattern(prefix, suffix), QaEmailPattern("", "@${QaMemberCreator.EMAIL_DOMAIN}")).distinct()
    }

    companion object {
        const val KEY = "{key}"
        const val DEFAULT_EMAIL_TEMPLATE = "${QaMemberCreator.EMAIL_LOCAL_PREFIX}$KEY@${QaMemberCreator.EMAIL_DOMAIN}"
        private const val SAMPLE_KEY = "00000000-0000-0000-0000-000000000000"
    }
}
