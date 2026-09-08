package org.jbnu.jdevops.jcodeportallogin.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class ApiErrorResponse(
    val timestamp: Long = System.currentTimeMillis(),
    val status: Int,
    val code: String,
    val message: String,
    val path: String,
    val requestId: String,
    val fieldErrors: Map<String, String>? = null
)

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        exception: ResponseStatusException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val requestId = requestId(request)
        if (exception.statusCode.is5xxServerError) {
            logger.error("API dependency failure: requestId={}, path={}", requestId, request.requestURI, exception)
        } else {
            logger.warn(
                "API request rejected: requestId={}, status={}, path={}, reason={}",
                requestId, exception.statusCode.value(), request.requestURI, exception.reason
            )
        }
        return response(
            exception.statusCode,
            (exception.statusCode as? HttpStatus)?.name ?: "HTTP_${exception.statusCode.value()}",
            publicMessage(exception.statusCode, exception.reason),
            request,
            requestId
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val requestId = requestId(request)
        val fields = exception.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "입력값을 확인해주세요.") }
        logger.warn("API validation failed: requestId={}, path={}, fields={}", requestId, request.requestURI, fields.keys)
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "입력 정보를 확인해주세요.",
            request,
            requestId,
            fields
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val requestId = requestId(request)
        logger.warn("Unreadable API request: requestId={}, path={}", requestId, request.requestURI)
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST_BODY",
            "입력 정보를 확인해주세요.",
            request,
            requestId
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        exception: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val requestId = requestId(request)
        logger.warn("Invalid API argument: requestId={}, path={}", requestId, request.requestURI, exception)
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_ARGUMENT",
            "입력 정보를 확인해주세요.",
            request,
            requestId
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        exception: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val requestId = requestId(request)
        logger.warn("Database constraint rejected request: requestId={}, path={}", requestId, request.requestURI, exception)
        return response(
            HttpStatus.CONFLICT,
            "RESOURCE_CONFLICT",
            "이미 등록된 정보와 충돌합니다. 목록을 새로고침한 뒤 다시 확인해주세요.",
            request,
            requestId
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(
        exception: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val requestId = requestId(request)
        logger.error("Unhandled API error: requestId={}, path={}", requestId, request.requestURI, exception)
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.",
            request,
            requestId
        )
    }

    private fun publicMessage(status: HttpStatusCode, reason: String?): String {
        if (status.is5xxServerError) {
            return when (status.value()) {
                502, 503, 504 -> "외부 서비스와 통신하지 못했습니다. 잠시 후 다시 시도해주세요."
                else -> "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
            }
        }
        return when (status.value()) {
            401 -> "로그인이 필요하거나 세션이 만료되었습니다. 다시 로그인해주세요."
            403 -> reason.safeReason() ?: "이 작업을 수행할 권한이 없습니다."
            404 -> "요청한 정보를 찾을 수 없습니다."
            else -> reason.safeReason() ?: "요청을 처리할 수 없습니다. 입력과 현재 상태를 확인해주세요."
        }
    }

    private fun String?.safeReason(): String? = this
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 300 && '\n' !in it && '\r' !in it }

    private fun requestId(request: HttpServletRequest): String {
        val supplied = request.getHeader(REQUEST_ID_HEADER)?.trim()
        return supplied?.takeIf { REQUEST_ID_PATTERN.matches(it) } ?: UUID.randomUUID().toString()
    }

    private fun response(
        status: HttpStatusCode,
        code: String,
        message: String,
        request: HttpServletRequest,
        requestId: String,
        fieldErrors: Map<String, String>? = null
    ): ResponseEntity<ApiErrorResponse> = ResponseEntity
        .status(status)
        .header(REQUEST_ID_HEADER, requestId)
        .body(ApiErrorResponse(
            status = status.value(),
            code = code,
            message = message,
            path = request.requestURI,
            requestId = requestId,
            fieldErrors = fieldErrors
        ))

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-ID"
        private val REQUEST_ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}
