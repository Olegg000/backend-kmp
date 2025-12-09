package com.example.demo.features.notifications.service

import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class NotificationService(
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository
) {

    fun checkCuratorRosterStatus(curatorLogin: String): Map<String, Any> {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw RuntimeException("Куратор не найден")

        val group = curator.group
            ?: return mapOf("needsReminder" to false, "reason" to "Куратор не привязан к группе")

        // Следующий понедельник (начало недели, на которую нужно заполнить табель)
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        // Проверяем: Есть ли хотя бы одна запись на следующую неделю?
        val students = userRepository.findAllByGroup(group)
        val datesNextWeek = (0..4).map { nextMonday.plusDays(it.toLong()) }

        val hasPermissions = students.any { student ->
            permissionRepository.findAllByStudentAndDateIn(student, datesNextWeek).isNotEmpty()
        }

        // Считаем дни до дедлайна (пятница, 12:00)
        val deadline = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline).toInt()

        return mapOf(
            "needsReminder" to !hasPermissions,
            "daysUntilDeadline" to daysLeft,
            "deadlineDate" to deadline.toString()
        )
    }
}