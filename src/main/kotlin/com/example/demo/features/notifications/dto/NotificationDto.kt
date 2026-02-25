package com.example.demo.features.notifications.dto

import java.time.LocalDateTime

data class NotificationDto(
    val id: Long,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: LocalDateTime
)
