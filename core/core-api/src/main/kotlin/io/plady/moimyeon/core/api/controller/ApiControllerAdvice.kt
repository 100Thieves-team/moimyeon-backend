package io.plady.moimyeon.core.api.controller

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.response.ApiResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.logging.LogLevel
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

// admin-api 가 런타임에 함께 조립되므로 core 패키지의 컨트롤러에만 적용되도록 범위를 제한한다
@RestControllerAdvice(basePackages = ["io.plady.moimyeon.core"])
class ApiControllerAdvice {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> log.error("CoreException : {}", e.message, e)
            LogLevel.WARN -> log.warn("CoreException : {}", e.message, e)
            else -> log.info("CoreException : {}", e.message, e)
        }
        return ResponseEntity(ApiResponse.error(e.errorType, e.data), e.errorType.status)
    }

    @ExceptionHandler(CoreApiException::class)
    fun handleCoreApiException(e: CoreApiException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> log.error("CoreApiException : {}", e.message, e)
            LogLevel.WARN -> log.warn("CoreApiException : {}", e.message, e)
            else -> log.info("CoreApiException : {}", e.message, e)
        }
        return ResponseEntity(ApiResponse.error(e.errorType, e.data), e.errorType.status)
    }

    // 요청 형태 오류(수송 계층): Bean Validation 실패 — 어떤 필드가 왜 틀렸는지 data 로 전달
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Any>> {
        val fieldErrors = e.bindingResult.fieldErrors
            .groupBy({ it.field }, { it.defaultMessage ?: "invalid" })
            .mapValues { (_, messages) -> messages.joinToString("; ") }
        log.warn("MethodArgumentNotValidException : {}", fieldErrors)
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST, fieldErrors), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): 쿼리/경로 파라미터의 제약 위반(@Size 등)
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(e: HandlerMethodValidationException): ResponseEntity<ApiResponse<Any>> {
        val parameterErrors = e.parameterValidationResults
            .groupBy(
                { it.methodParameter.parameterName ?: "unknown" },
                { result -> result.resolvableErrors.joinToString("; ") { it.defaultMessage ?: "invalid" } },
            )
            .mapValues { (_, messages) -> messages.joinToString("; ") }
        log.warn("HandlerMethodValidationException : {}", parameterErrors)
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST, parameterErrors), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameter(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Any>> {
        log.warn("MissingServletRequestParameterException : {}", e.message)
        val data = mapOf(e.parameterName to "required parameter is missing")
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST, data), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): 쿼리/경로 파라미터 타입 불일치(UUID 아님, 숫자 아님 등)
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Any>> {
        log.warn("MethodArgumentTypeMismatchException : {}", e.message)
        val data = mapOf(e.name to "type mismatch")
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST, data), CoreApiErrorType.INVALID_REQUEST.status)
    }

    // 요청 형태 오류(수송 계층): 본문 해석 실패(깨진 JSON·필수 필드 누락·타입 불일치) — 클라이언트 잘못이므로 500 이 아니라 400
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Any>> {
        log.warn("HttpMessageNotReadableException : {}", e.message)
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.INVALID_REQUEST), CoreApiErrorType.INVALID_REQUEST.status)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("Exception : {}", e.message, e)
        return ResponseEntity(ApiResponse.error(CoreApiErrorType.DEFAULT_ERROR), CoreApiErrorType.DEFAULT_ERROR.status)
    }
}
