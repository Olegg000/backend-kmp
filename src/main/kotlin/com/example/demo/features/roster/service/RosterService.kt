package com.example.demo.features.roster.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.roster.dto.StudentRosterRow
import com.example.demo.features.roster.dto.UpdateRosterRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class RosterService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository
) {

    fun getRosterForGroup(
        curatorLogin: String,
        startDate: LocalDate,
        groupId: Int? = null
    ): List<StudentRosterRow> {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw RuntimeException("Куратор не найден")

        val curatorId = curator.id ?: throw RuntimeException("Куратор не имеет id")
        val curatorGroups = groupRepository.findAllByCuratorId(curatorId)
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

        val dates = (0..4).map { startDate.plusDays(it.toLong()) }

        return students.map { student ->
            val existingPermissions = permissionRepository.findAllByStudentAndDateIn(student, dates)
            val daysDto = dates.map { date ->
                val perm = existingPermissions.find { it.date == date }
                DayPermissionDto(
                    date = date,
                    isBreakfast = perm?.isBreakfastAllowed ?: false,
                    isLunch = perm?.isLunchAllowed ?: false,
                    reason = perm?.reason
                )
            }

            StudentRosterRow(
                studentId = student.id!!,
                fullName = "${student.surname} ${student.name}",
                days = daysDto
            )
        }
    }

    @Transactional
    fun updateRoster(req: UpdateRosterRequest, assignerLogin: String) {
        val student = userRepository.findById(req.studentId)
            .orElseThrow { RuntimeException("Студент не найден") }

        val assigner = userRepository.findByLogin(assignerLogin)
            ?: throw RuntimeException("Пользователь не найден")

        req.permissions.forEach { dto ->
            val existing = permissionRepository.findByStudentAndDate(student, dto.date)

            if (!dto.isBreakfast && !dto.isLunch) {
                if (existing != null) {
                    permissionRepository.delete(existing)
                }
                return@forEach
            }

            if (student.studentCategory == StudentCategory.MANY_CHILDREN && dto.isBreakfast && dto.isLunch) {
                throw RuntimeException("Категории 'Многодетные' можно назначить только один прием пищи в день")
            }

            val reasonText = dto.reason ?: "Общее основание"

            if (existing != null) {
                existing.isBreakfastAllowed = dto.isBreakfast
                existing.isLunchAllowed = dto.isLunch
                existing.reason = reasonText
                permissionRepository.save(existing)
            } else {
                permissionRepository.save(
                    MealPermissionEntity(
                        date = dto.date,
                        student = student,
                        assignedBy = assigner,
                        reason = reasonText,
                        isBreakfastAllowed = dto.isBreakfast,
                        isLunchAllowed = dto.isLunch
                    )
                )
            }
        }
    }
}
