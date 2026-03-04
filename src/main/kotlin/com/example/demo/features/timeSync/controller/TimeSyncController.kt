package com.example.demo.features.time.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RestController
@RequestMapping("/api/v1/time")
@Tag(name = "Time Sync", description = "Синхронизация времени для оффлайн QR")
class TimeSyncController(
    private val businessClock: Clock,
) {

    @GetMapping("/current")
    @Operation(summary = "Получить текущее время сервера (Unix timestamp)")
    fun getCurrentTime(): TimeResponse {
        val now = businessClock.instant()
        return TimeResponse(
            timestamp = now.epochSecond,
            iso8601 = now.toString()
        )
    }
}

data class TimeResponse(
    val timestamp: Long,
    val iso8601: String
)
