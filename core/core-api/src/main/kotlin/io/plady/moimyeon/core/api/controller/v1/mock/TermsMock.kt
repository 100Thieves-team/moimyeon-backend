package io.plady.moimyeon.core.api.controller.v1.mock

import io.plady.moimyeon.core.api.controller.v1.response.TermsResponse
import io.plady.moimyeon.core.enums.TermsType
import java.time.LocalDateTime
import java.util.UUID

// TODO(MOI-316): 약관 시드 모킹. 실 구현(terms 테이블·시드 데이터) 시 제거한다.
object TermsMock {
    val terms = listOf(
        TermsResponse(
            termsId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            type = TermsType.SERVICE,
            version = "v1.0",
            title = "모이면 이용약관",
            content = "제1조(목적) 이 약관은 모이면 서비스의 이용 조건과 절차를 규정합니다. (모킹 본문)",
            required = true,
            effectiveFrom = LocalDateTime.of(2026, 7, 1, 0, 0),
        ),
        TermsResponse(
            termsId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            type = TermsType.PRIVACY,
            version = "v1.0",
            title = "개인정보 처리방침",
            content = "모이면은 회원 가입과 서비스 제공을 위해 최소한의 개인정보를 수집·이용합니다. (모킹 본문)",
            required = true,
            effectiveFrom = LocalDateTime.of(2026, 7, 1, 0, 0),
        ),
    )
}
