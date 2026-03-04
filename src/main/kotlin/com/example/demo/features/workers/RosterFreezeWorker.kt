package com.example.demo.features.workers

import com.example.demo.features.roster.service.WeeklyRosterFreezeService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RosterFreezeWorker(
    private val weeklyRosterFreezeService: WeeklyRosterFreezeService,
) {
    private val log = LoggerFactory.getLogger(RosterFreezeWorker::class.java)

    @Scheduled(cron = "0 1 12 * * ?", zone = "\${app.business-zone:Europe/Samara}")
    fun freezeAfterDeadline() {
        val result = weeklyRosterFreezeService.freezeNextWeekIfLocked()
        if (result.skipped) {
            return
        }
        log.info(
            "Weekly roster freeze complete: weekStart={}, auditsCreated={}, missingCreated={}, snapshotDays={}",
            result.weekStart,
            result.auditsCreated,
            result.missingRosterPermissionsCreated,
            result.snapshotDays
        )
    }
}
