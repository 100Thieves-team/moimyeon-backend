package io.plady.moimyeon.admin.support.response

import io.plady.moimyeon.admin.support.error.AdminErrorMessage
import io.plady.moimyeon.admin.support.error.AdminErrorType

class AdminApiResponse<T> private constructor(
    val result: AdminResultType,
    val data: T? = null,
    val error: AdminErrorMessage? = null,
) {
    companion object {
        fun success(): AdminApiResponse<Any> {
            return AdminApiResponse(AdminResultType.SUCCESS, null, null)
        }

        fun <S> success(data: S): AdminApiResponse<S> {
            return AdminApiResponse(AdminResultType.SUCCESS, data, null)
        }

        fun <S> error(error: AdminErrorType, errorData: Any? = null): AdminApiResponse<S> {
            return AdminApiResponse(AdminResultType.ERROR, null, AdminErrorMessage(error, errorData))
        }
    }
}
