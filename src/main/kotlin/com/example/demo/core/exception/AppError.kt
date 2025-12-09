package com.example.demo.core.exception

data class AppError(
    val status: Int,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)