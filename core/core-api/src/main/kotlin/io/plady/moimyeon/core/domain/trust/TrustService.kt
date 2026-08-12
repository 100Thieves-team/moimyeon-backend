package io.plady.moimyeon.core.domain.trust

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TrustService {
    fun getPublicTrust(memberId: UUID): PublicTrust {
        return PublicTrust.empty()
    }
}
