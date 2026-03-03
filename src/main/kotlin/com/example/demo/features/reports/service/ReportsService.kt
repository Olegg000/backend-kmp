package com.example.demo.features.reports.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.reports.dto.AssignedByRole
import com.example.demo.features.reports.dto.AssignedByRoleFilter
import com.example.demo.features.reports.dto.ConsumptionReportRow
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime

@Service
class ReportsService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository,
    private val transactionRepository: MealTransactionRepository
) {

    fun generateConsumptionReport(
        currentLogin: String,
        startDate: LocalDate,
        endDate: LocalDate,
        groupId: Int?,
        assignedByRoleFilter: AssignedByRoleFilter
    ): List<ConsumptionReportRow> {
        if (endDate.isBefore(startDate)) {
            throw RuntimeException("Дата окончания не может быть раньше даты начала")
        }

        val currentUser = userRepository.findByLogin(currentLogin)
            ?: throw RuntimeException("Пользователь не найден")
        val groups = resolveAccessibleGroups(currentUser.roles, currentUser.id, groupId)

        val rows = mutableListOf<ConsumptionReportRow>()
        groups.forEach { group ->
            val permissions = permissionRepository.findAllByGroupAndDateRange(group, startDate, endDate)
            permissions.forEach { permission ->
                buildRowIfAccepted(permission, group, assignedByRoleFilter)?.let { rows += it }
            }
        }

        return rows.sortedWith(compareBy({ it.date }, { it.groupName }, { it.studentName }))
    }

    fun exportToCsv(
        currentLogin: String,
        startDate: LocalDate,
        endDate: LocalDate,
        groupId: Int?,
        assignedByRoleFilter: AssignedByRoleFilter
    ): String {
        val rows = generateConsumptionReport(currentLogin, startDate, endDate, groupId, assignedByRoleFilter)
        val header = "Дата,ID группы,Группа,Студент,Категория,Назначил,Завтрак использован,Обед использован\n"
        val body = rows.joinToString("\n") {
            val breakfast = if (it.breakfastUsed) "Да" else "Нет"
            val lunch = if (it.lunchUsed) "Да" else "Нет"
            "${it.date},${it.groupId},\"${it.groupName}\",\"${it.studentName}\"," +
                "${studentCategoryTitleRu(it.category)},${assignedByRoleTitleRu(it.assignedByRole)}," +
                "$breakfast,$lunch"
        }
        return header + body
    }

    private fun studentCategoryTitleRu(category: StudentCategory): String = when (category) {
        StudentCategory.SVO -> "СВО"
        StudentCategory.MANY_CHILDREN -> "Многодетные"
    }

    private fun assignedByRoleTitleRu(role: AssignedByRole): String = when (role) {
        AssignedByRole.ADMIN -> "Администратор"
        AssignedByRole.CURATOR -> "Куратор"
    }

    private fun resolveAccessibleGroups(
        roles: Set<Role>,
        userId: java.util.UUID?,
        groupId: Int?
    ): List<GroupEntity> {
        if (roles.contains(Role.ADMIN)) {
            if (groupId == null) return groupRepository.findAll()
            return listOf(groupRepository.findById(groupId).orElseThrow { RuntimeException("Группа не найдена") })
        }

        if (roles.contains(Role.CURATOR)) {
            val curatorId = userId ?: throw RuntimeException("Куратор не имеет идентификатор")
            val groups = groupRepository.findAllByCuratorId(curatorId)
            if (groupId == null) return groups
            val target = groups.firstOrNull { it.id == groupId } ?: throw RuntimeException("Группа недоступна куратору")
            return listOf(target)
        }

        throw RuntimeException("Недостаточно прав для просмотра отчетов")
    }

    private fun buildRowIfAccepted(
        permission: MealPermissionEntity,
        group: GroupEntity,
        assignedByRoleFilter: AssignedByRoleFilter
    ): ConsumptionReportRow? {
        val assignedByRole = if (permission.assignedBy.roles.contains(Role.ADMIN)) {
            AssignedByRole.ADMIN
        } else {
            AssignedByRole.CURATOR
        }

        if (assignedByRoleFilter == AssignedByRoleFilter.ADMIN && assignedByRole != AssignedByRole.ADMIN) return null
        if (assignedByRoleFilter == AssignedByRoleFilter.CURATOR && assignedByRole != AssignedByRole.CURATOR) return null

        val student = permission.student
        val category = student.studentCategory ?: throw RuntimeException("У студента ${student.login} отсутствует категория")

        val startOfDay = permission.date.atStartOfDay()
        val endOfDay = permission.date.atTime(LocalTime.MAX)

        return ConsumptionReportRow(
            date = permission.date,
            groupId = group.id ?: throw RuntimeException("У группы отсутствует id"),
            groupName = group.groupName,
            studentId = student.id ?: throw RuntimeException("У студента отсутствует id"),
            studentName = "${student.surname} ${student.name} ${student.fatherName}",
            category = category,
            assignedByRole = assignedByRole,
            breakfastUsed = transactionRepository.existsByStudentAndMealTypeAndTimeStampBetween(
                student,
                MealType.BREAKFAST,
                startOfDay,
                endOfDay
            ),
            lunchUsed = transactionRepository.existsByStudentAndMealTypeAndTimeStampBetween(
                student,
                MealType.LUNCH,
                startOfDay,
                endOfDay
            )
        )
    }
}
