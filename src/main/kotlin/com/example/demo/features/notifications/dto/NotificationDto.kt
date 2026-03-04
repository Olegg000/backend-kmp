package com.example.demo.features.notifications.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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

data class RosterDeadlineStatusDto(
    val cutoffDateTime: String,
    val weekStart: String,
    val isSubmitted: Boolean,
    val isLocked: Boolean,
    val severity: String,
    val needsReminder: Boolean,
    val daysUntilDeadline: Int? = null,
    val deadlineDate: String? = null,
    val reason: String? = null,
    val deadlineHuman: String? = null,
    val actionHint: String? = null,
)

data class PushTokenRegisterRequest(
    @field:NotBlank(message = "token обязателен")
    @field:Size(max = 1024, message = "token слишком длинный")
    val token: String,
    @field:NotBlank(message = "platform обязательна")
    val platform: String,
    val appVersion: String? = null,
    val locale: String? = null,
)

data class PushTokenUnregisterRequest(
    @field:NotBlank(message = "token обязателен")
    @field:Size(max = 1024, message = "token слишком длинный")
    val token: String,
)

data class PushSettingsResponse(
    val pushEnabled: Boolean,
)

data class UpdatePushSettingsRequest(
    val pushEnabled: Boolean,
)
