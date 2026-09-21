package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class MemberValidator(
    private val memberRepository: MemberRepository,
) {
    fun validateActive(memberId: UUID) {
        log.debug { "member.validator.validateActive memberId=$memberId" }
        val member = memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId)
            ?: throw CoreException(CoreErrorType.MEMBER_NOT_FOUND)
        requireBusiness(member.status == MemberStatus.ACTIVE, CoreErrorType.MEMBER_NOT_ACTIVE)
    }
}
