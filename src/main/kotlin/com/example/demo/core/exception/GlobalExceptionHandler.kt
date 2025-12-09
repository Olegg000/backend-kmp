package com.example.demo.core.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(e: RuntimeException): ResponseEntity<AppError> {
        logger.error("Runtime exception occurred", e)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(AppError(400, e.message ?: "Произошла ошибка"))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<AppError> {
        logger.warn("Access denied: ${e.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(AppError(403, "Нет прав доступа"))
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(e: BadCredentialsException): ResponseEntity<AppError> {
        logger.warn("Bad credentials attempt")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(AppError(401, "Неверный логин или пароль"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<AppError> {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") {
            "${it.field}: ${it.defaultMessage}"
        }
        logger.warn("Validation failed: $errors")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(AppError(400, "Ошибка валидации: $errors"))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<AppError> {
        logger.error("Illegal state", e)
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(AppError(409, e.message ?: "Конфликт состояния"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<AppError> {
        logger.error("Unexpected error", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(AppError(500, "Внутренняя ошибка сервера"))
    }
}