package com.example.demo.features.student.service

import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.student.dto.StudentSelfRosterDto
import com.example.demo.features.student.dto.StudentTodayMealsDto
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate

@Service
class StudentService(
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository
) {

    fun getSelfRoster(login: String, startDate: LocalDate?): StudentSelfRosterDto {
        val student = findStudentByLogin(login)

        val weekStart = (startDate ?: LocalDate.now()).with(DayOfWeek.MONDAY)
        val dates = (0..4).map { weekStart.plusDays(it.toLong()) }

        val permissions = permissionRepository
            .findAllByStudentAndDateIn(student, dates)
            .associateBy { it.date }

        val days = dates.map { date ->
            val p = permissions[date]
            DayPermissionDto(
                date = date,
                isBreakfast = p?.isBreakfastAllowed ?: false,
                isLunch = p?.isLunchAllowed ?: false,
                isDinner = p?.isDinnerAllowed ?: false,
                isSnack = p?.isSnackAllowed ?: false,
                isSpecial = p?.isSpecialAllowed ?: false,
                reason = p?.reason
            )
        }

        return StudentSelfRosterDto(
            studentId = student.id!!,
            fullName = "${student.surname} ${student.name}",
            groupName = student.group?.groupName,
            weekStart = weekStart,
            days = days
        )
    }

    fun getTodayMeals(login: String): StudentTodayMealsDto {
        val student = findStudentByLogin(login)
        val today = LocalDate.now()

        val p = permissionRepository.findByStudentAndDate(student, today)

        return StudentTodayMealsDto(
            date = today,
            isBreakfastAllowed = p?.isBreakfastAllowed ?: false,
            isLunchAllowed = p?.isLunchAllowed ?: false,
            isDinnerAllowed = p?.isDinnerAllowed ?: false,
            isSnackAllowed = p?.isSnackAllowed ?: false,
            isSpecialAllowed = p?.isSpecialAllowed ?: false,
            reason = p?.reason
        )
    }

    private fun findStudentByLogin(login: String): UserEntity =
        userRepository.findByLogin(login)
            ?: throw RuntimeException("Пользователь не найден: $login")
}