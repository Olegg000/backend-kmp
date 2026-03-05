package com.example.demo.features.reports.service

import com.example.demo.core.database.CuratorWeekFillStatus
import com.example.demo.core.database.MealType
import com.example.demo.core.database.NoMealReasonType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.repository.CuratorWeekAuditRepository
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.core.logging.maskLogin
import com.example.demo.core.logging.maskUuid
import com.example.demo.features.reports.dto.AssignedByRole
import com.example.demo.features.reports.dto.AssignedByRoleFilter
import com.example.demo.features.reports.dto.ConsumptionReportRow
import com.example.demo.features.reports.dto.ReportAccessScope
import com.example.demo.features.reports.dto.ConsumptionSummaryDay
import com.example.demo.features.reports.dto.ConsumptionSummaryResponse
import com.example.demo.features.reports.dto.ZeroFillCuratorSummary
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ReportsService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository,
    private val transactionRepository: MealTransactionRepository,
    private val curatorWeekAuditRepository: CuratorWeekAuditRepository,
    private val rosterWeekPolicy: RosterWeekPolicy,
) {
    private val logger = LoggerFactory.getLogger(ReportsService::class.java)

    fun generateConsumptionReport(
        currentLogin: String,
        startDate: LocalDate,
        endDate: LocalDate,
        groupId: Int?,
        assignedByRoleFilter: AssignedByRoleFilter,
        accessScope: ReportAccessScope = ReportAccessScope.AUTO,
    ): List<ConsumptionReportRow> {
        val loginMasked = maskLogin(currentLogin)
        var currentUserId: UUID? = null
        try {
            if (endDate.isBefore(startDate)) {
                throw BusinessException(
                    code = "INVALID_DATE_RANGE",
                    userMessage = "Дата окончания не может быть раньше даты начала",
                    status = HttpStatus.BAD_REQUEST,
                )
            }
            val today = rosterWeekPolicy.today()
            if (startDate.isAfter(today) || endDate.isAfter(today)) {
                throw BusinessException(
                    code = "FUTURE_REPORT_DATE_NOT_ALLOWED",
                    userMessage = "Отчет можно формировать только по текущую дату",
                    status = HttpStatus.BAD_REQUEST,
                )
            }

            val currentUser = userRepository.findByLogin(currentLogin)
                ?: throw BusinessException(
                    code = "USER_NOT_FOUND",
                    userMessage = "Пользователь не найден",
                    status = HttpStatus.NOT_FOUND,
                )
            currentUserId = currentUser.id
            logger.info(
                "Preparing consumption report: loginMasked={}, userIdMasked={}, roles={}, startDate={}, endDate={}, groupId={}, assignedByRoleFilter={}",
                loginMasked,
                maskUuid(currentUserId),
                currentUser.roles.sortedBy { it.name }.joinToString(","),
                startDate,
                endDate,
                groupId,
                assignedByRoleFilter
            )

            val groups = resolveAccessibleGroups(
                roles = currentUser.roles,
                userId = currentUser.id,
                groupId = groupId,
                accessScope = accessScope,
            )
            logger.info(
                "Resolved accessible groups for report: loginMasked={}, groupsCount={}, groupIds={}",
                loginMasked,
                groups.size,
                groups.mapNotNull { it.id }.sorted().joinToString(",")
            )
            if (groups.isEmpty()) {
                logger.info("No accessible groups for report: loginMasked={}, groupId={}", loginMasked, groupId)
                return emptyList()
            }

            val dates = buildDateRange(startDate, endDate)
            val studentsByGroup = groups.associateWith { group ->
                userRepository.findAllByGroup(group)
                    .asSequence()
                    .filter { it.roles.contains(Role.STUDENT) }
                    .sortedWith(compareBy({ it.surname }, { it.name }, { it.fatherName }))
                    .toList()
            }
            val studentsCount = studentsByGroup.values.sumOf { it.size }
            logger.info(
                "Loaded students for report: loginMasked={}, groupsCount={}, studentsCount={}",
                loginMasked,
                groups.size,
                studentsCount
            )

            val permissions = permissionRepository.findAllByGroupsAndDateRange(groups, startDate, endDate)
            logger.info("Loaded meal permissions for report: loginMasked={}, permissionsCount={}", loginMasked, permissions.size)
            val permissionByStudentDate = mutableMapOf<StudentDateKey, MealPermissionEntity>()
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
            logger.info("Loaded meal transactions for report: loginMasked={}, transactionsCount={}", loginMasked, transactions.size)
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
                val groupIdValue = group.id ?: throw BusinessException(
                    code = "GROUP_ID_MISSING",
                    userMessage = "У группы отсутствует идентификатор",
                    status = HttpStatus.CONFLICT,
                )
                studentsByGroup[group].orEmpty().forEach { student ->
                    val studentId = student.id ?: throw BusinessException(
                        code = "STUDENT_ID_MISSING",
                        userMessage = "У студента отсутствует идентификатор",
                        status = HttpStatus.CONFLICT,
                    )
                    val studentName = fullName(student.surname, student.name, student.fatherName)
                    for (date in dates) {
                        val permission = permissionByStudentDate[StudentDateKey(studentId, date)]
                        val isSyntheticMissingRoster = permission == null &&
                            group.curators.isNullOrEmpty() &&
                            rosterWeekPolicy.isWeekday(date)
                        val noMealReasonType = permission?.noMealReasonType ?: if (isSyntheticMissingRoster) {
                            NoMealReasonType.MISSING_ROSTER
                        } else {
                            null
                        }
                        val noMealReasonText = permission?.noMealReasonText ?: if (isSyntheticMissingRoster) {
                            "Куратор не назначен, табель не заполнен"
                        } else {
                            null
                        }
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
                            lunchScannedByName = lunchTx?.chef?.let { fullName(it.surname, it.name, it.fatherName) },
                            plannedBreakfast = permission?.isBreakfastAllowed == true,
                            plannedLunch = permission?.isLunchAllowed == true,
                            noMealReasonType = noMealReasonType,
                            noMealReasonText = noMealReasonText,
                            absenceFrom = permission?.absenceFrom,
                            absenceTo = permission?.absenceTo,
                            comment = permission?.comment,
                            isSyntheticMissingRoster = permission?.noMealReasonType == NoMealReasonType.MISSING_ROSTER ||
                                isSyntheticMissingRoster,
                        )
                    }
                }
            }

            val sortedRows = rows.sortedWith(compareBy({ it.date }, { it.groupName }, { it.studentName }))
            logger.info(
                "Consumption report prepared: loginMasked={}, userIdMasked={}, rowsCount={}",
                loginMasked,
                maskUuid(currentUserId),
                sortedRows.size
            )
            return sortedRows
        } catch (e: Exception) {
            logger.error(
                "Failed to generate consumption report: loginMasked={}, userIdMasked={}, startDate={}, endDate={}, groupId={}, assignedByRoleFilter={}, exceptionClass={}",
                loginMasked,
                maskUuid(currentUserId),
                startDate,
                endDate,
                groupId,
                assignedByRoleFilter,
                e::class.java.simpleName,
                e
            )
            throw e
        }
    }

    fun generateConsumptionSummary(
        currentLogin: String,
        startDate: LocalDate,
        endDate: LocalDate,
        groupId: Int?,
        assignedByRoleFilter: AssignedByRoleFilter,
        accessScope: ReportAccessScope = ReportAccessScope.AUTO,
    ): ConsumptionSummaryResponse {
        if (endDate.isBefore(startDate)) {
            throw BusinessException(
                code = "INVALID_DATE_RANGE",
                userMessage = "Дата окончания не может быть раньше даты начала",
                status = HttpStatus.BAD_REQUEST,
            )
        }

        val rows = generateConsumptionReport(
            currentLogin = currentLogin,
            startDate = startDate,
            endDate = endDate,
            groupId = groupId,
            assignedByRoleFilter = assignedByRoleFilter,
            accessScope = accessScope,
        )

        val days = buildDateRange(startDate, endDate).map { date ->
            val dateRows = rows.filter { it.date == date }
            ConsumptionSummaryDay(
                date = date,
                breakfastCount = dateRows.count { it.plannedBreakfast },
                lunchCount = dateRows.count { it.plannedLunch },
                bothCount = dateRows.count { it.plannedBreakfast && it.plannedLunch },
            )
        }
        val usedBreakfastCount = rows.count { it.breakfastUsed }
        val usedLunchCount = rows.count { it.lunchUsed }
        val usedBothCount = rows.count { it.breakfastUsed && it.lunchUsed }

        val currentUser = userRepository.findByLogin(currentLogin)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )

        val weekStartFrom = rosterWeekPolicy.weekStart(startDate)
        val weekStartTo = rosterWeekPolicy.weekStart(endDate)
        val audits = curatorWeekAuditRepository.findAllByWeekStartBetween(weekStartFrom, weekStartTo)

        val filteredAudits = when {
            currentUser.roles.contains(Role.ADMIN) -> audits
            currentUser.roles.contains(Role.CURATOR) -> audits.filter { it.curator.id == currentUser.id }
            else -> emptyList()
        }

        val zeroFillCurators = filteredAudits
            .filter { it.fillStatus == CuratorWeekFillStatus.ZERO_FILL }
            .map { audit ->
                val curatorId = audit.curator.id ?: throw BusinessException(
                    code = "CURATOR_ID_MISSING",
                    userMessage = "У куратора отсутствует идентификатор",
                    status = HttpStatus.CONFLICT,
                )
                val groupIds = groupRepository.findAllByCuratorId(curatorId)
                    .mapNotNull { it.id }
                    .sorted()
                ZeroFillCuratorSummary(
                    curatorId = curatorId,
                    curatorName = fullName(audit.curator.surname, audit.curator.name, audit.curator.fatherName),
                    weekStart = audit.weekStart,
                    groupIds = groupIds,
                    filledCells = audit.filledCells,
                    expectedCells = audit.expectedCells,
                    fillStatus = audit.fillStatus,
                )
            }
            .sortedWith(compareBy({ it.weekStart }, { it.curatorName }))

        return ConsumptionSummaryResponse(
            startDate = startDate,
            endDate = endDate,
            days = days,
            totalBreakfastCount = days.sumOf { it.breakfastCount },
            totalLunchCount = days.sumOf { it.lunchCount },
            totalBothCount = days.sumOf { it.bothCount },
            usedBreakfastCount = usedBreakfastCount,
            usedLunchCount = usedLunchCount,
            usedBothCount = usedBothCount,
            missingRosterRowsCount = rows.count { it.noMealReasonType == NoMealReasonType.MISSING_ROSTER },
            zeroFillCurators = zeroFillCurators,
        )
    }

    fun exportToCsv(
        currentLogin: String,
        startDate: LocalDate,
        endDate: LocalDate,
        groupId: Int?,
        assignedByRoleFilter: AssignedByRoleFilter,
        accessScope: ReportAccessScope = ReportAccessScope.AUTO,
    ): String {
        val rows = generateConsumptionReport(
            currentLogin = currentLogin,
            startDate = startDate,
            endDate = endDate,
            groupId = groupId,
            assignedByRoleFilter = assignedByRoleFilter,
            accessScope = accessScope,
        )
        val totals = calculateReportTotals(rows)
        val header =
            "Дата,ID группы,Группа,ID студента,Студент,Категория,Куратор," +
                "План завтрак,План обед,Причина непитания,Текст причины,Период с,Период по,Комментарий," +
                "Завтрак использован,ID транзакции завтрака,ФИО сканировавшего завтрак," +
                "Обед использован,ID транзакции обеда,ФИО сканировавшего обед," +
                "Статус/Причина (сводно),Период отсутствия (сводно),Комментарий (сводно),Категория (RU)\n"
        val body = rows.joinToString("\n") {
            listOf(
                it.date.toString(),
                it.groupId.toString(),
                it.groupName,
                it.studentId.toString(),
                it.studentName,
                it.category?.name ?: "-",
                buildAssignedByLabel(it.assignedByName),
                yesNo(it.plannedBreakfast),
                yesNo(it.plannedLunch),
                noMealReasonTypeTitleRu(it.noMealReasonType),
                it.noMealReasonText ?: "-",
                it.absenceFrom?.toString() ?: "-",
                it.absenceTo?.toString() ?: "-",
                it.comment ?: "-",
                yesNo(it.breakfastUsed),
                it.breakfastTransactionId?.toString() ?: "-",
                it.breakfastScannedByName ?: "-",
                yesNo(it.lunchUsed),
                it.lunchTransactionId?.toString() ?: "-",
                it.lunchScannedByName ?: "-",
                buildNoMealStatusSummary(it.noMealReasonType, it.noMealReasonText),
                buildAbsencePeriodSummary(it.absenceFrom, it.absenceTo),
                it.comment ?: "-",
                studentCategoryTitleRu(it.category),
            ).joinToString(",") { value -> csvEscape(value) }
        }
        val summaryRows = listOf(
            csvLine("ИТОГИ"),
            csvLine("Блок", "Метрика", "Значение", "Пояснение"),
            csvLine("ПО ФАКТУ", "Завтраки (количество питаний за все дни)", totals.factBreakfastCount.toString(), "Сумма по дням"),
            csvLine("ПО ФАКТУ", "Обеды (количество питаний за все дни)", totals.factLunchCount.toString(), "Сумма по дням"),
            csvLine("ПО ФАКТУ", "Завтрак и обед", totals.factBothCount.toString(), "Строки, где были оба факта"),
            csvLine("ПО ФАКТУ", "Всего питаний за все дни", totals.factMealEventsTotal.toString(), "Завтраки + обеды"),
            csvLine("ПО ФАКТУ", "Уникальных студентов питалось", totals.factUniqueStudentsCount.toString(), "Без повторов за период"),
            csvLine("ВСЕГО ДОЛЖНО БЫЛО ПИТАТЬСЯ", "Завтраки (количество назначений за все дни)", totals.plannedBreakfastCount.toString(), "Сумма по дням"),
            csvLine("ВСЕГО ДОЛЖНО БЫЛО ПИТАТЬСЯ", "Обеды (количество назначений за все дни)", totals.plannedLunchCount.toString(), "Сумма по дням"),
            csvLine("ВСЕГО ДОЛЖНО БЫЛО ПИТАТЬСЯ", "Завтрак и обед", totals.plannedBothCount.toString(), "Строки, где назначены оба приема"),
            csvLine("ВСЕГО ДОЛЖНО БЫЛО ПИТАТЬСЯ", "Всего назначений за все дни", totals.plannedMealEventsTotal.toString(), "Завтраки + обеды"),
            csvLine("ВСЕГО ДОЛЖНО БЫЛО ПИТАТЬСЯ", "Уникальных студентов с назначением", totals.plannedUniqueStudentsCount.toString(), "Без повторов за период"),
        )

        return buildString {
            append(header)
            if (body.isNotBlank()) {
                append(body)
            }
            append("\n\n")
            append(summaryRows.joinToString("\n"))
        }
    }

    internal fun calculateReportTotals(rows: List<ConsumptionReportRow>): ReportTotals {
        val factBreakfastCount = rows.count { it.breakfastUsed }
        val factLunchCount = rows.count { it.lunchUsed }
        val factBothCount = rows.count { it.breakfastUsed && it.lunchUsed }
        val factMealEventsTotal = factBreakfastCount + factLunchCount
        val factUniqueStudentsCount = rows.asSequence()
            .filter { it.breakfastUsed || it.lunchUsed }
            .map { it.studentId }
            .toSet()
            .size

        val plannedBreakfastCount = rows.count { it.plannedBreakfast }
        val plannedLunchCount = rows.count { it.plannedLunch }
        val plannedBothCount = rows.count { it.plannedBreakfast && it.plannedLunch }
        val plannedMealEventsTotal = plannedBreakfastCount + plannedLunchCount
        val plannedUniqueStudentsCount = rows.asSequence()
            .filter { it.plannedBreakfast || it.plannedLunch }
            .map { it.studentId }
            .toSet()
            .size

        return ReportTotals(
            factBreakfastCount = factBreakfastCount,
            factLunchCount = factLunchCount,
            factBothCount = factBothCount,
            factMealEventsTotal = factMealEventsTotal,
            factUniqueStudentsCount = factUniqueStudentsCount,
            plannedBreakfastCount = plannedBreakfastCount,
            plannedLunchCount = plannedLunchCount,
            plannedBothCount = plannedBothCount,
            plannedMealEventsTotal = plannedMealEventsTotal,
            plannedUniqueStudentsCount = plannedUniqueStudentsCount,
        )
    }

    private fun studentCategoryTitleRu(category: StudentCategory?): String = when (category) {
        null -> "-"
        StudentCategory.SVO -> "СВО"
        StudentCategory.MANY_CHILDREN -> "Многодетные"
    }

    private fun noMealReasonTypeTitleRu(reasonType: NoMealReasonType?): String = when (reasonType) {
        null -> "-"
        NoMealReasonType.EXPELLED -> "Отчислен"
        NoMealReasonType.SICK_LEAVE -> "Больничный"
        NoMealReasonType.OTHER -> "Иное"
        NoMealReasonType.MISSING_ROSTER -> "Куратор не заполнил табель"
    }

    private fun buildNoMealStatusSummary(reasonType: NoMealReasonType?, reasonText: String?): String {
        val status = noMealReasonTypeTitleRu(reasonType)
        val text = reasonText?.trim().orEmpty()
        if (status == "-" && text.isBlank()) return "-"
        if (status == "-") return text
        if (reasonType == NoMealReasonType.OTHER && text.isNotBlank()) return text
        return status
    }

    private fun buildAbsencePeriodSummary(absenceFrom: LocalDate?, absenceTo: LocalDate?): String {
        if (absenceFrom == null && absenceTo == null) return "-"
        val from = absenceFrom?.toString() ?: "?"
        val to = absenceTo?.toString() ?: "?"
        return "$from - $to"
    }

    private fun buildAssignedByLabel(assignedByName: String?): String =
        assignedByName?.takeIf { it.isNotBlank() } ?: "Не назначен"

    private fun resolveAccessibleGroups(
        roles: Set<Role>,
        userId: UUID?,
        groupId: Int?,
        accessScope: ReportAccessScope,
    ): List<GroupEntity> {
        if (accessScope == ReportAccessScope.CURATOR) {
            if (!roles.contains(Role.CURATOR)) {
                throw BusinessException(
                    code = "REPORT_ACCESS_DENIED",
                    userMessage = "Недостаточно прав для просмотра отчетов куратора",
                    status = HttpStatus.FORBIDDEN,
                )
            }
            return resolveCuratorGroups(userId, groupId)
        }

        if (accessScope == ReportAccessScope.ADMIN) {
            if (!roles.contains(Role.ADMIN)) {
                throw BusinessException(
                    code = "REPORT_ACCESS_DENIED",
                    userMessage = "Недостаточно прав для просмотра админских отчетов",
                    status = HttpStatus.FORBIDDEN,
                )
            }
            return resolveAdminGroups(groupId)
        }

        if (roles.contains(Role.ADMIN)) {
            return resolveAdminGroups(groupId)
        }

        if (roles.contains(Role.CURATOR)) {
            return resolveCuratorGroups(userId, groupId)
        }

        throw BusinessException(
            code = "REPORT_ACCESS_DENIED",
            userMessage = "Недостаточно прав для просмотра отчетов",
            status = HttpStatus.FORBIDDEN,
        )
    }

    private fun resolveAdminGroups(groupId: Int?): List<GroupEntity> {
        if (groupId == null) return groupRepository.findAll()
        return listOf(
            groupRepository.findById(groupId).orElseThrow {
                BusinessException(
                    code = "GROUP_NOT_FOUND",
                    userMessage = "Группа не найдена",
                    status = HttpStatus.NOT_FOUND,
                )
            }
        )
    }

    private fun resolveCuratorGroups(userId: UUID?, groupId: Int?): List<GroupEntity> {
        val curatorId = userId ?: throw BusinessException(
            code = "CURATOR_ID_MISSING",
            userMessage = "У куратора отсутствует идентификатор",
            status = HttpStatus.CONFLICT,
        )
        val groups = groupRepository.findAllByCuratorId(curatorId)
        if (groupId == null) return groups
        val target = groups.firstOrNull { it.id == groupId } ?: throw BusinessException(
            code = "CURATOR_GROUP_ACCESS_DENIED",
            userMessage = "Группа недоступна куратору",
            status = HttpStatus.FORBIDDEN,
        )
        return listOf(target)
    }

    private fun resolveAssignedByRole(permission: MealPermissionEntity): AssignedByRole {
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

    private fun csvLine(vararg values: String): String = values.joinToString(",") { csvEscape(it) }

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

internal data class ReportTotals(
    val factBreakfastCount: Int,
    val factLunchCount: Int,
    val factBothCount: Int,
    val factMealEventsTotal: Int,
    val factUniqueStudentsCount: Int,
    val plannedBreakfastCount: Int,
    val plannedLunchCount: Int,
    val plannedBothCount: Int,
    val plannedMealEventsTotal: Int,
    val plannedUniqueStudentsCount: Int,
)
