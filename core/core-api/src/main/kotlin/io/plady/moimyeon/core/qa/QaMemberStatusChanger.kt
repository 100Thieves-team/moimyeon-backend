package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.MemberManager
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.support.error.CoreException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaMemberStatusChanger(
    private val qaMemberFinder: QaMemberFinder,
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
) {
    // 이미 그 상태면 그대로 둔다. QA 스크립트가 앞 실행의 상태를 몰라도 같은 결과가 나오게 하기 위해서다.
    fun change(memberId: UUID, status: MemberStatus): QaMemberStatus {
        log.debug { "qa-member.status-changer.change memberId=$memberId status=$status" }
        qaMemberFinder.requireQaMember(memberId)
        val before = memberFinder.getById(memberId).status
        if (before != status) {
            try {
                when (status) {
                    MemberStatus.RESTRICTED -> memberManager.restrict(memberId)
                    MemberStatus.ACTIVE -> memberManager.reactivate(memberId)
                }
            } catch (e: CoreException) {
                // 같은 요청이 거의 동시에 와서 다른 쪽이 먼저 바꿨으면 이미 목표 상태다. 그때는 성공으로 본다
                if (memberFinder.getById(memberId).status != status) throw e
            }
        }
        return QaMemberStatus(memberId = memberId, before = before, status = status)
    }
}
