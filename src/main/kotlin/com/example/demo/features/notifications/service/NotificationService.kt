package com.example.demo.features.notifications.service

import com.example.demo.core.database.entity.NotificationEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.NotificationRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.notifications.dto.NotificationDto
import com.example.demo.features.notifications.dto.NotificationPageDto
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
            ?: throw RuntimeException("Curator not found")

        val group = curator.group
            ?: return mapOf("needsReminder" to false, "reason" to "Curator is not assigned to group")

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

    fun getUserNotificationsPage(login: String, cursor: Long?, limit: Int): NotificationPageDto {
        val user = userRepository.findByLogin(login) ?: return NotificationPageDto(emptyList(), null, false)

        val sorted = notificationRepository.findAllByUserOrderByCreatedAtDesc(user)
            .sortedByDescending { it.id ?: Long.MIN_VALUE }

        val filtered = if (cursor == null) sorted else sorted.filter { (it.id ?: Long.MIN_VALUE) < cursor }
        val pageItems = filtered.take(limit)
        val nextCursor = pageItems.lastOrNull()?.id
        val hasMore = filtered.size > pageItems.size

        return NotificationPageDto(
            items = pageItems.map {
                NotificationDto(
                    id = it.id!!,
                    title = it.title,
                    message = it.message,
                    isRead = it.isRead,
                    createdAt = it.createdAt.toString()
                )
            },
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    @Transactional
    fun markAsRead(login: String, notificationId: Long) {
        val user = userRepository.findByLogin(login) ?: throw RuntimeException("User not found")
        val notification = notificationRepository.findById(notificationId).orElseThrow { RuntimeException("Notification not found") }
        if (notification.user.id == user.id) {
            notification.isRead = true
            notificationRepository.save(notification)
        }
    }

    @Transactional
    fun markAsReadBatch(login: String, ids: List<Long>) {
        if (ids.isEmpty()) return
        val user = userRepository.findByLogin(login) ?: throw RuntimeException("User not found")
        ids.forEach { id ->
            val notification = notificationRepository.findById(id).orElse(null) ?: return@forEach
            if (notification.user.id == user.id && !notification.isRead) {
                notification.isRead = true
                notificationRepository.save(notification)
            }
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

