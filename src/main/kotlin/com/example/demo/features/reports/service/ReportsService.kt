package com.example.demo.features.reports.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealTransactionEntity
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
import java.util.UUID

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
        if (groups.isEmpty()) return emptyList()

        val dates = buildDateRange(startDate, endDate)
        val studentsByGroup = groups.associateWith { group ->
            userRepository.findAllByGroup(group)
                .asSequence()
                .filter { it.roles.contains(Role.STUDENT) }
                .sortedWith(compareBy({ it.surname }, { it.name }, { it.fatherName }))
                .toList()
        }

        val permissions = permissionRepository.findAllByGroupsAndDateRange(groups, startDate, endDate)
        val permissionByStudentDate = mutableMapOf<StudentDateKey, com.example.demo.core.database.entity.MealPermissionEntity>()
        permissions.forEach { permission ->
            val studentId = permission.student.id ?: return@forEach
            val key = StudentDateKey(studentId, permission.date)
            val previous = permissionByStudentDate[key]
            if (previous == null || (permission.id ?: Int.MIN_VALUE) > (previous.id ?: Int.MIN_VALUE)) {
                permissionByStudentDate[key] = permission
            }
        }

        val transactions = transactionRepository.findAllByStudentGroupInAndTimeStampBetween(
            groups = groups,
            start = startDate.atStartOfDay(),
            end = endDate.atTime(LocalTime.MAX)
        )
        val transactionByStudentDateMeal = mutableMapOf<StudentDateMealKey, MealTransactionEntity>()
        transactions.forEach { transaction ->
            val studentId = transaction.student.id ?: return@forEach
            val key = StudentDateMealKey(studentId, transaction.timeStamp.toLocalDate(), transaction.mealType)
            val previous = transactionByStudentDateMeal[key]
            if (previous == null || isNewerTransaction(transaction, previous)) {
                transactionByStudentDateMeal[key] = transaction
            }
        }

        val rows = mutableListOf<ConsumptionReportRow>()
        groups.forEach { group ->
            val groupIdValue = group.id ?: throw RuntimeException("У группы отсутствует id")
            studentsByGroup[group].orEmpty().forEach { student ->
                val studentId = student.id ?: throw RuntimeException("У студента отсутствует id")
                val studentName = fullName(student.surname, student.name, student.fatherName)
                for (date in dates) {
                    val permission = permissionByStudentDate[StudentDateKey(studentId, date)]
                    val assignedByRole = permission?.let(::resolveAssignedByRole)
                    if (assignedByRoleFilter == AssignedByRoleFilter.ADMIN && assignedByRole != AssignedByRole.ADMIN) continue
                    if (assignedByRoleFilter == AssignedByRoleFilter.CURATOR && assignedByRole != AssignedByRole.CURATOR) continue

                    val breakfastTx = transactionByStudentDateMeal[
                        StudentDateMealKey(studentId, date, MealType.BREAKFAST)
                    ]
                    val lunchTx = transactionByStudentDateMeal[
                        StudentDateMealKey(studentId, date, MealType.LUNCH)
                    ]

                    rows += ConsumptionReportRow(
                        date = date,
                        groupId = groupIdValue,
                        groupName = group.groupName,
                        studentId = studentId,
                        studentName = studentName,
                        category = student.studentCategory,
                        assignedByRole = assignedByRole,
                        assignedByName = permission?.assignedBy?.let { fullName(it.surname, it.name, it.fatherName) },
                        breakfastUsed = breakfastTx != null,
                        breakfastTransactionId = breakfastTx?.id,
                        breakfastScannedByName = breakfastTx?.chef?.let { fullName(it.surname, it.name, it.fatherName) },
                        lunchUsed = lunchTx != null,
                        lunchTransactionId = lunchTx?.id,
                        lunchScannedByName = lunchTx?.chef?.let { fullName(it.surname, it.name, it.fatherName) }
                    )
                }
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
        val header =
            "Дата,ID группы,Группа,ID студента,Студент,Категория,Роль назначившего,ФИО назначившего," +
                "Завтрак использован,ID транзакции завтрака,ФИО сканировавшего завтрак," +
                "Обед использован,ID транзакции обеда,ФИО сканировавшего обед\n"
        val body = rows.joinToString("\n") {
            listOf(
                it.date.toString(),
                it.groupId.toString(),
                it.groupName,
                it.studentId.toString(),
                it.studentName,
                studentCategoryTitleRu(it.category),
                assignedByRoleTitleRu(it.assignedByRole),
                it.assignedByName ?: "-",
                yesNo(it.breakfastUsed),
                it.breakfastTransactionId?.toString() ?: "-",
                it.breakfastScannedByName ?: "-",
                yesNo(it.lunchUsed),
                it.lunchTransactionId?.toString() ?: "-",
                it.lunchScannedByName ?: "-"
            ).joinToString(",") { value -> csvEscape(value) }
        }
        return header + body
    }

    private fun studentCategoryTitleRu(category: StudentCategory?): String = when (category) {
        null -> "-"
        StudentCategory.SVO -> "СВО"
        StudentCategory.MANY_CHILDREN -> "Многодетные"
    }

    private fun assignedByRoleTitleRu(role: AssignedByRole?): String = when (role) {
        null -> "-"
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

    private fun resolveAssignedByRole(permission: com.example.demo.core.database.entity.MealPermissionEntity): AssignedByRole {
        return if (permission.assignedBy.roles.contains(Role.ADMIN)) {
            AssignedByRole.ADMIN
        } else {
            AssignedByRole.CURATOR
        }
    }

    private fun buildDateRange(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            dates += cursor
            cursor = cursor.plusDays(1)
        }
        return dates
    }

    private fun isNewerTransaction(candidate: MealTransactionEntity, current: MealTransactionEntity): Boolean {
        return when {
            candidate.timeStamp.isAfter(current.timeStamp) -> true
            candidate.timeStamp.isBefore(current.timeStamp) -> false
            else -> (candidate.id ?: Int.MIN_VALUE) > (current.id ?: Int.MIN_VALUE)
        }
    }

    private fun fullName(surname: String, name: String, fatherName: String?): String {
        return listOf(surname, name, fatherName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
    }

    private fun yesNo(flag: Boolean): String = if (flag) "Да" else "Нет"

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private data class StudentDateKey(
        val studentId: UUID,
        val date: LocalDate
    )

    private data class StudentDateMealKey(
        val studentId: UUID,
        val date: LocalDate,
        val mealType: MealType
    )
}
