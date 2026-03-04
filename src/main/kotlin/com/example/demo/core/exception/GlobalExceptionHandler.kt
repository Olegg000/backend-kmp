package com.example.demo.core.exception

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
        logger.warn("Business exception [{}]: {}", e.code, e.message)
        return buildError(
            status = e.status,
            code = e.code,
            message = e.code,
            userMessage = e.userMessage,
            retryable = e.retryable
        )
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(e: RuntimeException): ResponseEntity<AppError> {
        logger.error("Unhandled runtime exception", e)
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_RUNTIME_ERROR",
            message = "INTERNAL_SERVER_ERROR",
            userMessage = "Internal server error. Please retry later.",
            retryable = true
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<AppError> {
        logger.warn("Access denied: ${e.message}")
        return buildError(
            status = HttpStatus.FORBIDDEN,
            code = "ACCESS_DENIED",
            message = "ACCESS_DENIED",
            userMessage = "You do not have access to this action",
            retryable = false
        )
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(e: BadCredentialsException): ResponseEntity<AppError> {
        logger.warn("Bad credentials")
        return buildError(
            status = HttpStatus.UNAUTHORIZED,
            code = "INVALID_CREDENTIALS",
            message = "INVALID_CREDENTIALS",
            userMessage = "Invalid login or password",
            retryable = false
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<AppError> {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn("Validation failed: $errors")
        return buildError(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = "VALIDATION_ERROR",
            userMessage = "Validation error: $errors",
            retryable = false
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<AppError> {
        logger.warn("Invalid request argument: {}", e.message)
        return buildError(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_ARGUMENT",
            message = "INVALID_ARGUMENT",
            userMessage = "Invalid request payload",
            retryable = false,
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<AppError> {
        logger.error("Illegal state", e)
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "ILLEGAL_STATE",
            message = "ILLEGAL_STATE",
            userMessage = "Operation failed. Please retry later.",
            retryable = true
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<AppError> {
        logger.error("Unexpected exception", e)
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = "INTERNAL_SERVER_ERROR",
            userMessage = "Internal server error. Please retry later.",
            retryable = true
        )
    }

    private fun buildError(
        status: HttpStatus,
        code: String,
        message: String,
        userMessage: String,
        retryable: Boolean
    ): ResponseEntity<AppError> {
        val requestId = UUID.randomUUID().toString()
        return ResponseEntity.status(status).body(
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
}
