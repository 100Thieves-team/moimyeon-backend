package io.plady.moimyeon.core.support.error

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * [CoreErrorType]이 메시지를 이미 내장하므로 표준 require 같은 메시지 람다는 두지 않는다.
 * 동적 상세는 [data]로 실어 응답/로그에 전달한다.
 */
@OptIn(ExperimentalContracts::class)
fun requireBusiness(condition: Boolean, errorType: CoreErrorType, data: Any? = null) {
    contract { returns() implies condition }
    if (!condition) throw CoreException(errorType, data)
}

@OptIn(ExperimentalContracts::class)
fun <T : Any> requireFound(value: T?, errorType: CoreErrorType, data: Any? = null): T {
    contract { returns() implies (value != null) }
    return value ?: throw CoreException(errorType, data)
}
