package com.example.demo.features.workers

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.retention", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DataRetentionWorker(
    private val dataRetentionService: DataRetentionService,
) {
    private val log = LoggerFactory.getLogger(DataRetentionWorker::class.java)

    // Ежедневная очистка в 02:30 по Самаре.
    @Scheduled(cron = "0 30 2 * * ?", zone = "\${app.business-zone:Europe/Samara}")
    fun runCleanup() {
        runCatching {
            val result = dataRetentionService.cleanup()
            val summary = result.entries.joinToString(", ") { "${it.key}=${it.value}" }
            log.info("Data retention cleanup completed: {}", summary)
        }.onFailure { e ->
            log.error("Data retention cleanup failed", e)
        }
    }
}
