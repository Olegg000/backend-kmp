package com.example.demo.features.qr.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.qr.dto.StudentKeyDto
import com.example.demo.features.qr.dto.StudentPermissionDto
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ChefDataService(
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository
) {

    /**
     * Возвращает публичные ключи всех студентов, у которых есть ключи.
     * Повар скачивает их для оффлайн ECDSA верификации.
     */
    fun getAllStudentKeys(): List<StudentKeyDto> {
        return userRepository.findAll()
            .filter { it.roles.contains(Role.STUDENT) && it.publicKey != null }
            .map { user ->
                StudentKeyDto(
                    userId = user.id!!,
                    publicKey = user.publicKey!!,
                    name = user.name,
                    surname = user.surname,
                    fatherName = user.fatherName,
                    groupName = user.group?.groupName
                )
            }
    }

    /**
     * Возвращает разрешения на питание для всех студентов на сегодня.
     * Повар скачивает для оффлайн-проверки разрешений.
     */
    fun getTodayPermissions(): List<StudentPermissionDto> {
        val today = LocalDate.now()
        val permissions = permissionRepository.findAllByDate(today)

        return permissions.map { perm ->
            val student = perm.student
            StudentPermissionDto(
                studentId = student.id!!,
                name = student.name,
                surname = student.surname,
                breakfast = perm.isBreakfastAllowed,
                lunch = perm.isLunchAllowed,
                dinner = perm.isDinnerAllowed,
                snack = perm.isSnackAllowed,
                special = perm.isSpecialAllowed
            )
        }
    }
}
