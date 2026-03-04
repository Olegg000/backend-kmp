package com.example.demo.core.exception

import com.example.demo.core.logging.RequestCorrelationFilter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<AppError> {
        val requestId = resolveRequestId()
        logger.warn(
            "Business exception: code={}, status={}, requestId={}, exceptionClass={}, message={}",
            e.code,
            e.status.value(),
            requestId,
            e::class.java.simpleName,
            e.message
        )
        return buildError(
            status = e.status,
            code = e.code,
            message = e.code,
            userMessage = e.userMessage,
            retryable = e.retryable,
            requestId = requestId
        )
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(e: RuntimeException): ResponseEntity<AppError> {
        val requestId = resolveRequestId()
        logger.error(
            "Unhandled runtime exception: code={}, status={}, requestId={}, exceptionClass={}, message={}",
            "INTERNAL_RUNTIME_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            requestId,
            e::class.java.simpleName,
            e.message,
            e
        )
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_RUNTIME_ERROR",
            message = "INTERNAL_SERVER_ERROR",
            userMessage = "Internal server error. Please retry later.",
            retryable = true,
            requestId = requestId
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<AppError> {
        val requestId = resolveRequestId()
        logger.warn(
            "Access denied: code={}, status={}, requestId={}, exceptionClass={}, message={}",
            "ACCESS_DENIED",
            HttpStatus.FORBIDDEN.value(),
            requestId,
            e::class.java.simpleName,
            e.message
        )
        return buildError(
            status = HttpStatus.FORBIDDEN,
            code = "ACCESS_DENIED",
            message = "ACCESS_DENIED",
            userMessage = "You do not have access to this action",
            retryable = false,
            requestId = requestId
        )
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(e: BadCredentialsException): ResponseEntity<AppError> {
        val requestId = resolveRequestId()
        logger.warn(
            "Bad credentials: code={}, status={}, requestId={}, exceptionClass={}",
            "INVALID_CREDENTIALS",
            HttpStatus.UNAUTHORIZED.value(),
            requestId,
            e::class.java.simpleName
        )
        return buildError(
            status = HttpStatus.UNAUTHORIZED,
            code = "INVALID_CREDENTIALS",
            message = "INVALID_CREDENTIALS",
            userMessage = "Invalid login or password",
            retryable = false,
            requestId = requestId
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<AppError> {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        val requestId = resolveRequestId()
        logger.warn(
            "Validation failed: code={}, status={}, requestId={}, exceptionClass={}, details={}",
            "VALIDATION_ERROR",
            HttpStatus.BAD_REQUEST.value(),
            requestId,
            e::class.java.simpleName,
            errors
        )
        return buildError(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = "VALIDATION_ERROR",
            userMessage = "Validation error: $errors",
            retryable = false,
            requestId = requestId
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<AppError> {
        val requestId = resolveRequestId()
        logger.warn(
            "Invalid request argument: code={}, status={}, requestId={}, exceptionClass={}, message={}",
            "INVALID_ARGUMENT",
            HttpStatus.BAD_REQUEST.value(),
            requestId,
            e::class.java.simpleName,
            e.message
        )
        return buildError(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_ARGUMENT",
            message = "INVALID_ARGUMENT",
            userMessage = "Invalid request payload",
            retryable = false,
            requestId = requestId
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<AppError> {
        val requestId = resolveRequestId()
        logger.error(
            "Illegal state: code={}, status={}, requestId={}, exceptionClass={}, message={}",
            "ILLEGAL_STATE",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            requestId,
            e::class.java.simpleName,
            e.message,
            e
        )
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "ILLEGAL_STATE",
            message = "ILLEGAL_STATE",
            userMessage = "Operation failed. Please retry later.",
            retryable = true,
            requestId = requestId
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<AppError> {
        val requestId = resolveRequestId()
        logger.error(
            "Unexpected exception: code={}, status={}, requestId={}, exceptionClass={}, message={}",
            "INTERNAL_SERVER_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            requestId,
            e::class.java.simpleName,
            e.message,
            e
        )
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = "INTERNAL_SERVER_ERROR",
            userMessage = "Internal server error. Please retry later.",
            retryable = true,
            requestId = requestId
        )
    }

    private fun buildError(
        status: HttpStatus,
        code: String,
        message: String,
        userMessage: String,
        retryable: Boolean,
        requestId: String
    ): ResponseEntity<AppError> {
        return ResponseEntity.status(status)
            .header(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId)
            .body(
                AppError(
                    code = code,
                    message = message,
                    userMessage = userMessage,
                    retryable = retryable,
                    status = status.value(),
                    requestId = requestId
                )
            )
    }

    private fun resolveRequestId(): String {
        return RequestCorrelationFilter.currentRequestId() ?: UUID.randomUUID().toString()
    }
}
