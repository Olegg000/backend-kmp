package com.example.demo.features.time.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/time")
@Tag(name = "Time Sync", description = "Синхронизация времени для оффлайн QR")
class TimeSyncController {

    @GetMapping("/current")
    @Operation(summary = "Получить текущее время сервера (Unix timestamp)")
    fun getCurrentTime(): TimeResponse {
        return TimeResponse(
            timestamp = System.currentTimeMillis() / 1000, // Unix timestamp в секундах
            iso8601 = java.time.Instant.now().toString()
        )
    }
}

data class TimeResponse(
    val timestamp: Long,
    val iso8601: String
)