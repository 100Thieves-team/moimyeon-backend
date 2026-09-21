package io.plady.moimyeon.core.api.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.boot.logging.LogLevel
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException

private val log = KotlinLogging.logger {}

// admin-api 가 런타임에 함께 조립되므로 core 패키지의 컨트롤러에만 적용되도록 범위를 제한한다
@RestControllerAdvice(basePackages = ["io.plady.moimyeon.core"])
class ApiControllerAdvice {
    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> log.error(e) { "exception.core code=${e.errorType.code} message=${e.message}" }
            LogLevel.WARN -> log.warn(e) { "exception.core code=${e.errorType.code} message=${e.message}" }
            else -> log.info(e) { "exception.core code=${e.errorType.code} message=${e.message}" }
        }
        return ResponseEntity(ApiResponse.error(e.errorType, e.data), e.errorType.status)
    }

    @ExceptionHandler(CoreApiException::class)
    fun handleCoreApiException(e: CoreApiException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> log.error(e) { "exception.core-api code=${e.errorType.code} message=${e.message}" }
            LogLevel.WARN -> log.warn(e) { "exception.core-api code=${e.errorType.code} message=${e.message}" }
            else -> log.info(e) { "exception.core-api code=${e.errorType.code} message=${e.message}" }
        }
        return ResponseEntity(ApiResponse.error(e.errorType, e.data), e.errorType.status)
    }

    // 요청 형태 오류(수송 계층): 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameter(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Any>> {
        log.warn(e) { "exception.transport type=MissingServletRequestParameter parameter=${e.parameterName}" }
        val data = mapOf(e.parameterName to "required parameter is missing")
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST, data), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): multipart 필수 파트 누락
    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingServletRequestPart(e: MissingServletRequestPartException): ResponseEntity<ApiResponse<Any>> {
        log.warn(e) { "exception.transport type=MissingServletRequestPart part=${e.requestPartName}" }
        val data = mapOf(e.requestPartName to "required part is missing")
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST, data), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): 서버 multipart 상한 초과
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(e: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Any>> {
        log.warn(e) { "exception.transport type=MaxUploadSizeExceeded maxUploadSize=${e.maxUploadSize}" }
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): 쿼리/경로 파라미터 타입 불일치(UUID 아님, 숫자 아님 등)
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Any>> {
        log.warn(e) { "exception.transport type=MethodArgumentTypeMismatch parameter=${e.name} requiredType=${e.requiredType?.simpleName}" }
        val data = mapOf(e.name to "type mismatch")
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST, data), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): 본문 해석 실패(깨진 JSON·필수 필드 누락·타입 불일치) — 클라이언트 잘못이므로 500 이 아니라 400
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Any>> {
        log.warn(e) { "exception.transport type=HttpMessageNotReadable cause=${e.cause?.javaClass?.simpleName}" }
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST), CoreApiErrorType.INVALID_REQUEST.status)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error(e) { "exception.unhandled type=${e.javaClass.name}" }
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.DEFAULT_ERROR), CoreApiErrorType.DEFAULT_ERROR.status)
    }
}
