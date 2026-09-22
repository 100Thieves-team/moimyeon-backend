package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaMemberFinder(
    private val qaTestDataRepository: QaTestDataRepository,
) {
    @Transactional(readOnly = true)
    fun getQaMembers(): List<QaMember> {
        log.debug { "qa-member.finder.getQaMembers" }
        return qaTestDataRepository.findQaMembers(QaMemberCreator.PROVIDER_ID_PREFIX, QaMemberCreator.EMAIL_DOMAIN)
            .map { QaMember(id = it.id, nickname = it.nickname, email = it.email) }
    }
}
