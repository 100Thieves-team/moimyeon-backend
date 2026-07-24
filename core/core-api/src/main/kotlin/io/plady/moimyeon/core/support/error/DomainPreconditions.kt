package io.plady.moimyeon.core.support.error

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * 비즈니스 규칙 검증용 require. 표준 `require`(IllegalArgumentException)와 달리 [CoreException]을 던져
 * [CoreErrorType]에 매핑된 정상 응답(4xx 등)으로 처리되게 한다.
 *
 * 구분 기준:
 * - 정상 흐름에서 도달 가능한 비즈니스 규칙 위반(중복 탈퇴, 중복 연결 등) → [requireBusiness]
 * - 도달하면 곧 버그인 구조 불변식(잘못된 초기화 등) → 표준 `require`/`check` (IAE/ISE → 500 fail-fast)
 *
 * [CoreErrorType]이 메시지를 이미 내장하므로 표준 require 같은 메시지 람다는 두지 않는다.
 * 동적 상세는 [data]로 실어 응답/로그에 전달한다. `contract` 덕에 호출 이후 스마트 캐스트가 동작한다.
 */
@OptIn(ExperimentalContracts::class)
fun requireBusiness(condition: Boolean, errorType: CoreErrorType, data: Any? = null) {
    contract { returns() implies condition }
    if (!condition) throw CoreException(errorType, data)
}

/** [value]가 null이면 [CoreException]을 던지고, 아니면 non-null로 좁혀 반환한다(not-found 등). */
@OptIn(ExperimentalContracts::class)
fun <T : Any> requireFound(value: T?, errorType: CoreErrorType, data: Any? = null): T {
    contract { returns() implies (value != null) }
    return value ?: throw CoreException(errorType, data)
}
