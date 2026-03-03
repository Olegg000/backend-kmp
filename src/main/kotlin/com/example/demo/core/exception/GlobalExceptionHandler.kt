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
            technicalMessage = e.message ?: e.userMessage,
            userMessage = e.userMessage,
            retryable = e.retryable
        )
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(e: RuntimeException): ResponseEntity<AppError> {
        logger.error("Runtime exception", e)
        return buildError(
            status = HttpStatus.BAD_REQUEST,
            code = "RUNTIME_ERROR",
            technicalMessage = e.message ?: "Runtime error",
            userMessage = e.message ?: "Request cannot be processed",
            retryable = false
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<AppError> {
        logger.warn("Access denied: ${e.message}")
        return buildError(
            status = HttpStatus.FORBIDDEN,
            code = "ACCESS_DENIED",
            technicalMessage = e.message ?: "Access denied",
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
            technicalMessage = e.message ?: "Invalid credentials",
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
            technicalMessage = errors,
            userMessage = "Validation error: $errors",
            retryable = false
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<AppError> {
        logger.error("Illegal state", e)
        return buildError(
            status = HttpStatus.CONFLICT,
            code = "ILLEGAL_STATE",
            technicalMessage = e.message ?: "State conflict",
            userMessage = e.message ?: "State conflict",
            retryable = false
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<AppError> {
        logger.error("Unexpected exception", e)
        return buildError(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            technicalMessage = e.message ?: "Internal server error",
            userMessage = "Internal server error. Please retry later.",
            retryable = true
        )
    }

    private fun buildError(
        status: HttpStatus,
        code: String,
        technicalMessage: String,
        userMessage: String,
        retryable: Boolean
    ): ResponseEntity<AppError> {
        val requestId = UUID.randomUUID().toString()
        return ResponseEntity.status(status).body(
            AppError(
                code = code,
                message = technicalMessage,
                userMessage = userMessage,
                retryable = retryable,
                status = status.value(),
                requestId = requestId
            )
        )
    }
}
