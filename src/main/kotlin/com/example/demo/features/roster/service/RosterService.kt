package com.example.demo.features.roster.service

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.NoMealReasonType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.CuratorWeekSubmissionEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.CuratorWeekSubmissionRepository
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.notifications.service.NotificationService
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.roster.dto.StudentRosterRow
import com.example.demo.features.roster.dto.UpdateRosterRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class RosterService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository,
    private val weekSubmissionRepository: CuratorWeekSubmissionRepository,
    private val notificationService: NotificationService,
    private val rosterWeekPolicy: RosterWeekPolicy,
    @Value("\${app.test-mode.enabled:false}")
    private val testModeEnabled: Boolean,
) {

    fun getRosterForGroup(
        curatorLogin: String,
        startDate: LocalDate,
        groupId: Int? = null
    ): List<StudentRosterRow> {
        val curator = requireCurator(curatorLogin)
        val weekStart = rosterWeekPolicy.weekStart(startDate)
        ensureWeekReadable(weekStart)

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
            .sortedWith(compareBy({ it.surname }, { it.name }, { it.fatherName }))

        val dates = rosterWeekPolicy.weekDates(weekStart)

        return students.map { student ->
            val existingPermissions = permissionRepository.findAllByStudentAndDateIn(student, dates)
                .associateBy { it.date }
            val daysDto = dates.map { date ->
                val perm = existingPermissions[date]
                DayPermissionDto(
                    date = date,
                    isBreakfast = perm?.isBreakfastAllowed ?: false,
                    isLunch = perm?.isLunchAllowed ?: false,
                    reason = perm?.reason,
                    noMealReasonType = perm?.noMealReasonType,
                    noMealReasonText = perm?.noMealReasonText,
                    absenceFrom = perm?.absenceFrom,
                    absenceTo = perm?.absenceTo,
                    comment = perm?.comment,
                )
            }

            StudentRosterRow(
                studentId = student.id!!,
                fullName = "${student.surname} ${student.name}",
                studentCategory = student.studentCategory,
                days = daysDto
            )
        }
    }

    @Transactional
    fun updateRoster(req: UpdateRosterRequest, assignerLogin: String) {
        val student = userRepository.findById(req.studentId)
            .orElseThrow {
                BusinessException(
                    code = "STUDENT_NOT_FOUND",
                    userMessage = "Студент не найден",
                    status = HttpStatus.NOT_FOUND,
                )
            }

        val assigner = requireCurator(assignerLogin)
        val assignerId = assigner.id ?: throw BusinessException(
            code = "CURATOR_ID_MISSING",
            userMessage = "У куратора отсутствует идентификатор",
            status = HttpStatus.CONFLICT,
        )
        val studentGroupId = student.group?.id ?: throw BusinessException(
            code = "STUDENT_GROUP_NOT_SET",
            userMessage = "Студент не привязан к группе",
            status = HttpStatus.CONFLICT,
        )
        if (!groupRepository.existsByIdAndCuratorId(studentGroupId, assignerId)) {
            throw BusinessException(
                code = "CURATOR_GROUP_ACCESS_DENIED",
                userMessage = "Можно обновлять табель только студентов своих групп",
                status = HttpStatus.FORBIDDEN,
            )
        }

        if (student.studentCategory == null) {
            throw BusinessException(
                code = "STUDENT_CATEGORY_REQUIRED",
                userMessage = "Нельзя назначить питание: у студента не указана категория. Перейдите в назначение категории."
            )
        }

        if (student.accountStatus == AccountStatus.FROZEN_EXPELLED) {
            throw BusinessException(
                code = "STUDENT_FROZEN_EXPELLED",
                userMessage = "Студент отчислен и заморожен. Назначение питания запрещено.",
                status = HttpStatus.CONFLICT,
            )
        }

        val touchedWeeks = linkedSetOf<LocalDate>()

        req.permissions.forEach { dto ->
            validateDateCanBeEdited(dto.date)
            touchedWeeks += rosterWeekPolicy.weekStart(dto.date)

            if (student.studentCategory == StudentCategory.MANY_CHILDREN && dto.isBreakfast && dto.isLunch) {
                throw BusinessException(
                    code = "MANY_CHILDREN_LIMIT",
                    userMessage = "Категории 'Многодетные' можно назначить только один прием пищи в день",
                    status = HttpStatus.BAD_REQUEST,
                )
            }

            val validatedNoMeal = validateNoMealPayload(dto)
            val existing = permissionRepository.findByStudentAndDate(student, dto.date)
            val permission = existing ?: MealPermissionEntity(
                date = dto.date,
                student = student,
                assignedBy = assigner,
                reason = "Общее основание",
                isBreakfastAllowed = dto.isBreakfast,
                isLunchAllowed = dto.isLunch,
            )

            permission.isBreakfastAllowed = dto.isBreakfast
            permission.isLunchAllowed = dto.isLunch
            permission.reason = resolveReasonText(dto, validatedNoMeal.reasonType)
            permission.noMealReasonType = validatedNoMeal.reasonType
            permission.noMealReasonText = validatedNoMeal.reasonText
            permission.absenceFrom = validatedNoMeal.absenceFrom
            permission.absenceTo = validatedNoMeal.absenceTo
            permission.comment = dto.comment?.trim()?.ifBlank { null }

            permissionRepository.save(permission)

            if (!dto.isBreakfast && !dto.isLunch && validatedNoMeal.reasonType == NoMealReasonType.EXPELLED) {
                freezeStudentAsExpelled(student = student, actor = assigner, note = permission.comment ?: validatedNoMeal.reasonText)
            }
        }

        touchedWeeks.forEach { weekStart ->
            updateWeekSubmissionState(assigner, weekStart)
        }
    }

    private fun validateNoMealPayload(dto: DayPermissionDto): ValidatedNoMealPayload {
        if (dto.isBreakfast || dto.isLunch) {
            return ValidatedNoMealPayload()
        }

        val reasonType = dto.noMealReasonType
            ?: throw BusinessException(
                code = "NO_MEAL_REASON_REQUIRED",
                userMessage = "Для статуса без питания нужно выбрать причину.",
            )

        if (reasonType == NoMealReasonType.MISSING_ROSTER) {
            throw BusinessException(
                code = "NO_MEAL_REASON_INVALID",
                userMessage = "Причина «Куратор не заполнил табель» выставляется только системой после дедлайна.",
            )
        }

        val reasonText = dto.noMealReasonText?.trim()?.ifBlank { null }
        val absenceFrom = dto.absenceFrom
        val absenceTo = dto.absenceTo

        if (reasonType == NoMealReasonType.SICK_LEAVE || reasonType == NoMealReasonType.OTHER) {
            if (absenceFrom == null || absenceTo == null) {
                throw BusinessException(
                    code = "ABSENCE_RANGE_REQUIRED",
                    userMessage = "Для выбранной причины нужно указать период отсутствия.",
                )
            }
            if (absenceTo.isBefore(absenceFrom)) {
                throw BusinessException(
                    code = "ABSENCE_RANGE_INVALID",
                    userMessage = "Дата окончания периода отсутствия не может быть раньше даты начала.",
                )
            }
        }

        if (reasonType == NoMealReasonType.OTHER && reasonText.isNullOrBlank()) {
            throw BusinessException(
                code = "NO_MEAL_REASON_TEXT_REQUIRED",
                userMessage = "Для причины 'Иное' нужно заполнить текст причины.",
            )
        }

        return ValidatedNoMealPayload(
            reasonType = reasonType,
            reasonText = reasonText,
            absenceFrom = absenceFrom,
            absenceTo = absenceTo,
        )
    }

    private fun resolveReasonText(dto: DayPermissionDto, reasonType: NoMealReasonType?): String {
        val explicitReason = dto.reason?.trim()?.ifBlank { null }
        if (!explicitReason.isNullOrBlank()) return explicitReason

        val comment = dto.comment?.trim()?.ifBlank { null }
        if (!comment.isNullOrBlank()) return comment

        val noMealReason = dto.noMealReasonText?.trim()?.ifBlank { null }
        if (!noMealReason.isNullOrBlank()) return noMealReason

        return reasonType?.let(::noMealReasonTitleRu) ?: "Общее основание"
    }

    private fun validateDateCanBeEdited(date: LocalDate) {
        if (!rosterWeekPolicy.isWeekday(date)) {
            throw BusinessException(
                code = "ROSTER_WEEKEND_FORBIDDEN",
                userMessage = "Табель заполняется только на дни Пн–Пт.",
            )
        }

        val targetWeek = rosterWeekPolicy.weekStart(date)
        val minReadableWeek = minimumReadableWeek()
        if (targetWeek.isBefore(minReadableWeek)) {
            throw BusinessException(
                code = "ROSTER_ONLY_NEXT_WEEK_OR_LATER",
                userMessage = if (testModeEnabled) {
                    "Заполнять можно только текущую неделю и дальше."
                } else {
                    "Заполнять можно только следующую неделю и дальше."
                },
            )
        }

        if (testModeEnabled) {
            val nextWeek = rosterWeekPolicy.nextWeekStart()
            if (targetWeek.isBefore(nextWeek)) {
                throw BusinessException(
                    code = "ROSTER_ONLY_NEXT_WEEK_OR_LATER",
                    userMessage = "Текущая неделя доступна только для просмотра. Заполнять можно со следующей недели.",
                )
            }
            if (targetWeek == nextWeek && !rosterWeekPolicy.isDateEditable(date)) {
                throw BusinessException(
                    code = "ROSTER_WEEK_LOCKED",
                    userMessage = "После пятницы 12:00 табель на следующую неделю становится только для чтения.",
                )
            }
        } else if (!rosterWeekPolicy.isDateEditable(date)) {
            throw BusinessException(
                code = "ROSTER_WEEK_LOCKED",
                userMessage = "После пятницы 12:00 табель на следующую неделю становится только для чтения.",
            )
        }
    }

    private fun ensureWeekReadable(weekStart: LocalDate) {
        if (weekStart.isBefore(minimumReadableWeek())) {
            throw BusinessException(
                code = "ROSTER_ONLY_NEXT_WEEK_OR_LATER",
                userMessage = if (testModeEnabled) {
                    "Табель доступен только для текущей недели и далее."
                } else {
                    "Табель доступен только для следующей недели и далее."
                },
            )
        }
    }

    private fun minimumReadableWeek(): LocalDate {
        return if (testModeEnabled) {
            rosterWeekPolicy.weekStart(rosterWeekPolicy.today())
        } else {
            rosterWeekPolicy.nextWeekStart()
        }
    }

    private fun noMealReasonTitleRu(reasonType: NoMealReasonType): String = when (reasonType) {
        NoMealReasonType.EXPELLED -> "Отчислен"
        NoMealReasonType.SICK_LEAVE -> "Больничный"
        NoMealReasonType.OTHER -> "Иное"
        NoMealReasonType.MISSING_ROSTER -> "Куратор не заполнил табель"
    }

    private fun updateWeekSubmissionState(curator: UserEntity, weekStart: LocalDate) {
        val current = weekSubmissionRepository.findByCuratorAndWeekStart(curator, weekStart)
        val now = rosterWeekPolicy.now()
        val completed = isWeekFullyFilled(curator, weekStart)

        if (completed) {
            val isNew = current == null
            val saved = current ?: CuratorWeekSubmissionEntity(
                curator = curator,
                weekStart = weekStart,
                submittedAt = now,
                lastUpdatedAt = now,
            )
            if (!isNew) {
                saved.lastUpdatedAt = now
            }
            weekSubmissionRepository.save(saved)

            if (isNew) {
                notificationService.sendNotification(
                    user = curator,
                    title = "ТАБЕЛЬ ЗАПОЛНЕН",
                    message = "Табель на неделю с $weekStart заполнен полностью. Проверьте корректность данных перед дедлайном.",
                )
            }
            return
        }

        if (current != null) {
            current.lastUpdatedAt = now
            weekSubmissionRepository.save(current)
        }
    }

    private fun isWeekFullyFilled(curator: UserEntity, weekStart: LocalDate): Boolean {
        val curatorId = curator.id ?: return false
        val groups = groupRepository.findAllByCuratorId(curatorId)
        if (groups.isEmpty()) return false

        val students = groups
            .flatMap { group -> userRepository.findAllByGroup(group) }
            .asSequence()
            .filter { student -> student.roles.contains(Role.STUDENT) }
            .filter { student -> student.accountStatus != AccountStatus.FROZEN_EXPELLED }
            .distinctBy { it.id }
            .toList()

        if (students.isEmpty()) return false

        val dates = rosterWeekPolicy.weekDates(weekStart)
        val permissions = permissionRepository.findAllByStudentInAndDateBetween(students, dates.first(), dates.last())
        val existingKeys = permissions.mapNotNull { perm ->
            val studentId = perm.student.id ?: return@mapNotNull null
            studentId to perm.date
        }.toSet()

        return students.all { student ->
            val studentId = student.id ?: return@all false
            dates.all { date -> (studentId to date) in existingKeys }
        }
    }

    private fun freezeStudentAsExpelled(student: UserEntity, actor: UserEntity, note: String?) {
        if (student.accountStatus == AccountStatus.FROZEN_EXPELLED) return
        student.accountStatus = AccountStatus.FROZEN_EXPELLED
        student.expelledAt = rosterWeekPolicy.now()
        student.expelledBy = actor
        student.expelNote = note?.trim()?.ifBlank { null }
        userRepository.save(student)
    }

    private fun requireCurator(curatorLogin: String): UserEntity {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw BusinessException(
                code = "CURATOR_NOT_FOUND",
                userMessage = "Куратор не найден",
                status = HttpStatus.NOT_FOUND,
            )
        if (!curator.roles.contains(Role.CURATOR)) {
            throw BusinessException(
                code = "ROSTER_ACCESS_DENIED",
                userMessage = "Действие доступно только куратору",
                status = HttpStatus.FORBIDDEN,
            )
        }
        return curator
    }

    private data class ValidatedNoMealPayload(
        val reasonType: NoMealReasonType? = null,
        val reasonText: String? = null,
        val absenceFrom: LocalDate? = null,
        val absenceTo: LocalDate? = null,
    )
}
