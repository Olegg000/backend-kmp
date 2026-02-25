package com.example.demo.features.notifications.service

import com.example.demo.core.database.entity.NotificationEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.NotificationRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.notifications.dto.NotificationDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class NotificationService(
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository,
    private val notificationRepository: NotificationRepository
) {

    fun checkCuratorRosterStatus(curatorLogin: String): Map<String, Any> {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw RuntimeException("Куратор не найден")

        val group = curator.group
            ?: return mapOf("needsReminder" to false, "reason" to "Куратор не привязан к группе")

        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        val students = userRepository.findAllByGroup(group)
        val datesNextWeek = (0..4).map { nextMonday.plusDays(it.toLong()) }

        val hasPermissions = students.any { student ->
            permissionRepository.findAllByStudentAndDateIn(student, datesNextWeek).isNotEmpty()
        }

        val deadline = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline).toInt()

        return mapOf(
            "needsReminder" to !hasPermissions,
            "daysUntilDeadline" to daysLeft,
            "deadlineDate" to deadline.toString()
        )
    }

    fun getUnreadCount(login: String): Long {
        val user = userRepository.findByLogin(login) ?: return 0
        return notificationRepository.countByUserAndIsReadFalse(user)
    }

    fun getUserNotifications(login: String): List<NotificationDto> {
        val user = userRepository.findByLogin(login) ?: return emptyList()
        return notificationRepository.findAllByUserOrderByCreatedAtDesc(user).map {
            NotificationDto(it.id!!, it.title, it.message, it.isRead, it.createdAt)
        }
    }

    @Transactional
    fun markAsRead(login: String, notificationId: Long) {
        val user = userRepository.findByLogin(login) ?: throw RuntimeException("Пользователь не найден")
        val notification = notificationRepository.findById(notificationId).orElseThrow { RuntimeException("Уведомление не найдено") }
        if (notification.user.id == user.id) {
            notification.isRead = true
            notificationRepository.save(notification)
        }
    }

    @Transactional
    fun sendNotification(user: UserEntity, title: String, message: String) {
        val notification = NotificationEntity().apply {
            this.user = user
            this.title = title
            this.message = message
        }
        notificationRepository.save(notification)
    }
}
