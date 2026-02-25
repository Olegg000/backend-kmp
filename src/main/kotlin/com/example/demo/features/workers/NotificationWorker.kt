package com.example.demo.features.workers

import com.example.demo.core.database.Role
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.notifications.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NotificationWorker(
    private val userRepository: UserRepository,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(NotificationWorker::class.java)

    // Проверяем табели каждый день в 10:00
    @Scheduled(cron = "0 0 10 * * ?")
    fun checkRostersAndNotify() {
        log.info("Running NotificationWorker to check curators rosters...")
        val curators = userRepository.findAllByRole(Role.CURATOR)
        for (curator in curators) {
            try {
                val status = notificationService.checkCuratorRosterStatus(curator.login)
                val needsReminder = status["needsReminder"] as? Boolean ?: false
                
                if (needsReminder) {
                    val daysLeft = status["daysUntilDeadline"] as? Int ?: 0
                    notificationService.sendNotification(
                        user = curator,
                        title = "Напоминание о табеле",
                        message = "Вам необходимо заполнить табель питания на следующую неделю. Осталось дней до дедлайна: ${daysLeft}."
                    )
                }
            } catch (e: Exception) {
                log.error("Failed to check roster for ${curator.login}", e)
            }
        }
    }
}
