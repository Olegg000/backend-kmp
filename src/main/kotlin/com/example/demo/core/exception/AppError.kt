package com.example.demo.core.exception

data class AppError(
    val code: String,
    val message: String,
    val userMessage: String,
    val retryable: Boolean = false,
    val status: Int,
    val requestId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
