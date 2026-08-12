package io.plady.moimyeon.core.domain.trust

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TrustService(
    private val trustFinder: TrustFinder,
) {
    fun getPublicTrust(memberId: UUID): PublicTrust = trustFinder.getPublicTrust(memberId)
}
