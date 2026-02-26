package com.example.demo.features.notifications.dto

data class NotificationDto(
    val id: Long,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String
)

data class NotificationPageDto(
    val items: List<NotificationDto>,
    val nextCursor: Long?,
    val hasMore: Boolean
)

data class MarkReadBatchRequest(
    val ids: List<Long>
)
