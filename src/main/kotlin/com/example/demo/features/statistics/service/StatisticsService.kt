package com.example.demo.features.statistics.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.statistics.dto.StudentMealStatus
import org.springframework.http.HttpStatus
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
            ?: throw BusinessException(
                code = "CURATOR_NOT_FOUND",
                userMessage = "Куратор не найден",
                status = HttpStatus.NOT_FOUND,
            )

        val curatorId = curator.id ?: throw BusinessException(
            code = "CURATOR_ID_MISSING",
            userMessage = "У куратора отсутствует идентификатор",
            status = HttpStatus.CONFLICT,
        )
        val curatorGroups = groupRepository.findAllByCuratorId(curatorId)
        if (curatorGroups.isEmpty()) {
            throw BusinessException(
                code = "CURATOR_GROUP_ACCESS_DENIED",
                userMessage = "Куратор не привязан к группам",
                status = HttpStatus.FORBIDDEN,
            )
        }

        val targetGroup = if (groupId != null) {
            curatorGroups.firstOrNull { it.id == groupId }
                ?: throw BusinessException(
                    code = "CURATOR_GROUP_ACCESS_DENIED",
                    userMessage = "Группа недоступна куратору",
                    status = HttpStatus.FORBIDDEN,
                )
        } else {
            curatorGroups.minByOrNull { it.id ?: Int.MAX_VALUE }
                ?: throw BusinessException(
                    code = "GROUP_NOT_FOUND",
                    userMessage = "Группа не найдена",
                    status = HttpStatus.NOT_FOUND,
                )
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
                hadLunch = transactions.any { it.mealType == MealType.LUNCH }
            )
        }
    }
}
