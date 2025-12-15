package com.example.demo.features.roster.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.roster.dto.StudentRosterRow
import com.example.demo.features.roster.dto.UpdateRosterRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class RosterService(
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository
) {

    // Получить табель группы на указанную неделю (начиная с startDate)
    fun getRosterForGroup(curatorLogin: String, startDate: LocalDate): List<StudentRosterRow> {
        // 1. Ищем куратора и его студентов
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw RuntimeException("Куратор не найден")

        // ВАЖНО: Куратор видит ТОЛЬКО свою группу
        val group = curator.group
            ?: throw RuntimeException("Куратор не привязан к группе")

        // Находим студентов ТОЛЬКО из этой группы (куратор в табель не попадает)
        val students = userRepository.findAllByGroup(group)
            .filter { it.roles.contains(Role.STUDENT) }

        // 2. Генерируем даты (например, пн-пт)
        val dates = (0..4).map { startDate.plusDays(it.toLong()) } // 5 дней

        // 3. Собираем данные
        return students.map { student ->
            // Ищем существующие разрешения в БД
            val existingPermissions = permissionRepository.findAllByStudentAndDateIn(student, dates)

            val daysDto = dates.map { date ->
                val perm = existingPermissions.find { it.date == date }
                DayPermissionDto(
                    date = date,
                    isBreakfast = perm?.isBreakfastAllowed ?: false,
                    isLunch = perm?.isLunchAllowed ?: false,
                    isDinner = perm?.isDinnerAllowed ?: false,
                    isSnack = perm?.isSnackAllowed ?: false,
                    isSpecial = perm?.isSpecialAllowed ?: false
                )
            }

            StudentRosterRow(
                studentId = student.id!!,
                fullName = "${student.surname} ${student.name}",
                days = daysDto
            )
        }
    }

    // Сохранить изменения
    @Transactional
    fun updateRoster(req: UpdateRosterRequest, assignerLogin: String) {
        val student = userRepository.findById(req.studentId)
            .orElseThrow { RuntimeException("Студент не найден") }

        val assigner = userRepository.findByLogin(assignerLogin)!!

        req.permissions.forEach { dto ->
            val existing = permissionRepository.findByStudentAndDate(student, dto.date)

            // Логика: Если галочки сняты - удаляем запись или ставим false?
            // Лучше так: если все false - удаляем запись (чистим БД).
            if (!dto.isBreakfast && !dto.isLunch && !dto.isDinner &&
                !dto.isSnack && !dto.isSpecial) {
                if (existing != null) permissionRepository.delete(existing)
                return@forEach
            }

            // Если есть разрешение, должна быть причина
            val reasonText = dto.reason ?: "Общее основание" // Дефолтная причина

            if (existing != null) {
                existing.isBreakfastAllowed = dto.isBreakfast
                existing.isLunchAllowed = dto.isLunch
                existing.isDinnerAllowed = dto.isDinner
                existing.isSnackAllowed = dto.isSnack
                existing.isSpecialAllowed = dto.isSpecial
                existing.reason = reasonText
                // existing.assignedBy = assigner
                permissionRepository.save(existing)
            } else {
                permissionRepository.save(
                    MealPermissionEntity(
                        date = dto.date,
                        student = student,
                        assignedBy = assigner,
                        reason = reasonText,
                        isBreakfastAllowed = dto.isBreakfast,
                        isLunchAllowed = dto.isLunch,
                        isDinnerAllowed = dto.isDinner,
                        isSnackAllowed = dto.isSnack,
                        isSpecialAllowed = dto.isSpecial
                    )
                )
            }
        }
    }
}
