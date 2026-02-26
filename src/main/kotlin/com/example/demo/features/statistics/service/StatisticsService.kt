package com.example.demo.features.statistics.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.statistics.dto.StudentMealStatus
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime

@Service
class StatisticsService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: MealTransactionRepository
) {

    fun getGroupMealStatus(curatorLogin: String, date: LocalDate, groupId: Int? = null): List<StudentMealStatus> {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw RuntimeException("Куратор не найден")

        val curatorId = curator.id ?: throw RuntimeException("Куратор не имеет id")
        val curatorGroups = groupRepository.findAllByCurator_Id(curatorId)
        if (curatorGroups.isEmpty()) {
            throw RuntimeException("Куратор не привязан к группам")
        }

        val targetGroup = if (groupId != null) {
            curatorGroups.firstOrNull { it.id == groupId }
                ?: throw RuntimeException("Группа недоступна куратору")
        } else {
            curatorGroups.minByOrNull { it.id ?: Int.MAX_VALUE }
                ?: throw RuntimeException("Группа не найдена")
        }

        val students = userRepository.findAllByGroup(targetGroup)
            .filter { it.roles.contains(Role.STUDENT) }

        val startOfDay = date.atStartOfDay()
        val endOfDay = date.atTime(LocalTime.MAX)

        return students.map { student ->
            val transactions = transactionRepository.findAllByStudentAndTimeStampBetween(
                student,
                startOfDay,
                endOfDay
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
