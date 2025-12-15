package com.example.demo.features.statistics.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.statistics.dto.StudentMealStatus
import org.springframework.stereotype.Service
import com.example.demo.core.database.Role
import java.time.LocalDate
import java.time.LocalTime

@Service
class StatisticsService(
    private val userRepository: UserRepository,
    private val transactionRepository: MealTransactionRepository
) {

    fun getGroupMealStatus(curatorLogin: String, date: LocalDate): List<StudentMealStatus> {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw RuntimeException("Куратор не найден")

        val group = curator.group
            ?: throw RuntimeException("Куратор не привязан к группе")

        val students = userRepository.findAllByGroup(group)
            .filter { it.roles.contains(Role.STUDENT) }

        val startOfDay = date.atStartOfDay()
        val endOfDay = date.atTime(LocalTime.MAX)

        return students.map { student ->
            // Проверяем транзакции за этот день
            val transactions = transactionRepository.findAllByStudentAndTimeStampBetween(
                student, startOfDay, endOfDay
            )

            StudentMealStatus(
                studentId = student.id!!,
                fullName = "${student.surname} ${student.name}",
                hadBreakfast = transactions.any { it.mealType == MealType.BREAKFAST },
                hadLunch = transactions.any { it.mealType == MealType.LUNCH },
                hadDinner = transactions.any { it.mealType == MealType.DINNER },
                hadSnack = transactions.any { it.mealType == MealType.SNACK },
                hadSpecial = transactions.any { it.mealType == MealType.SPECIAL }
            )
        }
    }
}