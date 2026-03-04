package com.example.demo.features.workers

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.Role
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.notifications.service.NotificationService
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NotificationWorker(
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val rosterWeekPolicy: RosterWeekPolicy,
) {
    private val log = LoggerFactory.getLogger(NotificationWorker::class.java)

    // Утренние напоминания в 07:00 по Самаре.
    @Scheduled(cron = "0 0 7 * * ?", zone = "\${app.business-zone:Europe/Samara}")
    fun sendMorningReminders() {
        val now = rosterWeekPolicy.now()
        if (now.dayOfWeek == java.time.DayOfWeek.SATURDAY || now.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            return
        }

        try {
            notificationService.notifyStudentsMorningKeyReminder(now.toLocalDate())
            notificationService.notifyChefsMorningKeyReminder(now.toLocalDate())
        } catch (e: Exception) {
            log.error("Failed morning key reminders at {}", now, e)
        }

        log.info("Running curator morning reminder worker at {}", now)
        val curators = userRepository.findAllByRoleAndAccountStatus(Role.CURATOR, AccountStatus.ACTIVE)
        curators.forEach { curator ->
            runCatching {
                if (now.dayOfWeek != java.time.DayOfWeek.FRIDAY) {
                    notificationService.sendCuratorDailyReminderIfNeeded(curator)
                }
                notificationService.sendCuratorPostSubmitCheckInIfNeeded(curator)
            }.onFailure { e ->
                log.error("Failed curator morning reminder for {}", curator.login, e)
            }
        }
    }

    // Пятничная эскалация: каждый час с 07:00 до 11:59, только если нулевое заполнение.
    @Scheduled(cron = "0 0 * * * ?", zone = "\${app.business-zone:Europe/Samara}")
    fun sendFridayHourlyEscalation() {
        val now = rosterWeekPolicy.now()
        if (now.dayOfWeek != java.time.DayOfWeek.FRIDAY || now.hour !in 7..11) {
            return
        }

        log.info("Running friday hourly escalation worker at {}", now)
        val curators = userRepository.findAllByRoleAndAccountStatus(Role.CURATOR, AccountStatus.ACTIVE)
        curators.forEach { curator ->
            try {
                notificationService.sendCuratorHourlyReminderIfZeroFill(curator)
            } catch (e: Exception) {
                log.error("Failed hourly reminder for {}", curator.login, e)
            }
        }
    }

    // Напоминания поварам в окно подтверждения отчета:
    // пятница 12:05, суббота 07:05, воскресенье 07:05 (Samara).
    @Scheduled(cron = "0 5 * * * ?", zone = "\${app.business-zone:Europe/Samara}")
    fun sendChefReportConfirmationReminders() {
        val now = rosterWeekPolicy.now()
        val isFridayNoon = now.dayOfWeek == java.time.DayOfWeek.FRIDAY && now.hour == 12
        val isWeekendMorning =
            (now.dayOfWeek == java.time.DayOfWeek.SATURDAY || now.dayOfWeek == java.time.DayOfWeek.SUNDAY) &&
                now.hour == 7
        if (!isFridayNoon && !isWeekendMorning) {
            return
        }

        val weekStart = rosterWeekPolicy.nextWeekStart(now.toLocalDate())
        try {
            val sent = notificationService.notifyChefsWeeklyReportConfirmWindow(weekStart, now)
            if (sent > 0) {
                log.info("Chef weekly report notifications sent: weekStart={}, count={}", weekStart, sent)
            } else {
                log.info("Chef weekly report notifications already sent or no active chefs: weekStart={}", weekStart)
            }
        } catch (e: Exception) {
            log.error("Failed to notify chefs for weekStart={}", weekStart, e)
        }
    }
}
