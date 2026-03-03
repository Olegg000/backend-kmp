package com.example.demo.core.exception

import org.springframework.http.HttpStatus

class BusinessException(
    val code: String,
    val userMessage: String,
    val status: HttpStatus = HttpStatus.BAD_REQUEST,
    val retryable: Boolean = false,
    technicalMessage: String? = null,
) : RuntimeException(technicalMessage ?: userMessage)
