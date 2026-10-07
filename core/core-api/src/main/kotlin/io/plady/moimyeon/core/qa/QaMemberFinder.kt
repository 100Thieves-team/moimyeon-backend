package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaMemberFinder(
    private val qaTestDataRepository: QaTestDataRepository,
    private val properties: QaMemberProperties,
) {
    @Transactional(readOnly = true)
    fun getQaMembers(): List<QaMember> {
        log.debug { "qa-member.finder.getQaMembers" }
        return qaTestDataRepository.findQaMembers(QaMemberCreator.PROVIDER_ID_PREFIX, properties.emailPatterns())
            .map { QaMember(id = it.id, nickname = it.nickname, email = it.email) }
    }

    // 탈퇴한 QA 회원도 찾는다. 탈퇴 계정 복구를 dev 로그인으로 검증하려면 소셜 계정이 필요하다.
    @Transactional(readOnly = true)
    fun getSocialAccount(memberId: UUID): QaSocialAccount {
        log.debug { "qa-member.finder.getSocialAccount memberId=$memberId" }
        val member = findQaMember(memberId)
        // QA 회원 판정(SQL like)과 같게 대소문자를 가리지 않는다
        val account = member.socialAccounts().first { it.providerId.startsWith(QaMemberCreator.PROVIDER_ID_PREFIX, ignoreCase = true) }
        return QaSocialAccount(provider = account.provider, providerId = account.providerId, email = member.email)
    }

    @Transactional(readOnly = true)
    fun requireQaMember(memberId: UUID) {
        findQaMember(memberId)
    }

    private fun findQaMember(memberId: UUID): MemberEntity {
        val member = requireFound(qaTestDataRepository.findMember(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        requireBusiness(isQaMember(memberId), CoreErrorType.QA_DATA_ONLY)
        return member
    }

    private fun isQaMember(memberId: UUID): Boolean = qaTestDataRepository.isQaMember(memberId, QaMemberCreator.PROVIDER_ID_PREFIX, properties.emailPatterns())
}
