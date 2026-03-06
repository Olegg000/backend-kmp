package com.example.demo.features.curator.service

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.NoMealReasonType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.auth.dto.AdminUserDto
import com.example.demo.features.curator.dto.CuratorStudentAbsenceRequest
import com.example.demo.features.curator.dto.CuratorStudentRow
import com.example.demo.features.curator.dto.CuratorStudentCategoryUpdateRequest
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class CuratorStudentService(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val mealPermissionRepository: MealPermissionRepository,
    private val rosterWeekPolicy: RosterWeekPolicy,
) {

    @Transactional
    fun updateStudentCategory(
        curatorLogin: String,
        studentId: UUID,
        request: CuratorStudentCategoryUpdateRequest
    ): AdminUserDto {
        val curator = requireCurator(curatorLogin)
        val curatorId = curator.id ?: throw BusinessException(
            code = "CURATOR_ID_MISSING",
            userMessage = "Профиль куратора поврежден. Обратитесь к администратору.",
            status = HttpStatus.CONFLICT,
        )
        val student = userRepository.findById(studentId).orElseThrow {
            BusinessException(
                code = "STUDENT_NOT_FOUND",
                userMessage = "Студент не найден.",
                status = HttpStatus.NOT_FOUND,
            )
        }
        if (!student.roles.contains(Role.STUDENT)) {
            throw BusinessException(
                code = "TARGET_NOT_STUDENT",
                userMessage = "Можно менять категорию только студенту.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        val groupId = student.group?.id ?: throw BusinessException(
            code = "STUDENT_GROUP_REQUIRED",
            userMessage = "Студент не привязан к группе.",
            status = HttpStatus.CONFLICT,
        )
        if (!groupRepository.existsByIdAndCuratorId(groupId, curatorId)) {
            throw BusinessException(
                code = "GROUP_ACCESS_DENIED",
                userMessage = "Можно менять категорию только студентов своих групп.",
                status = HttpStatus.FORBIDDEN,
            )
        }
        if (student.accountStatus == AccountStatus.FROZEN_EXPELLED) {
            throw BusinessException(
                code = "STUDENT_FROZEN_EXPELLED",
                userMessage = "Студент отчислен и заморожен. Изменение категории запрещено.",
                status = HttpStatus.CONFLICT,
            )
        }

        student.studentCategory = request.studentCategory
        val saved = userRepository.save(student)
        return AdminUserDto(
            userId = saved.id!!,
            login = saved.login,
            roles = saved.roles,
            name = saved.name,
            surname = saved.surname,
            fatherName = saved.fatherName,
            groupId = saved.group?.id,
            studentCategory = saved.studentCategory,
            accountStatus = saved.accountStatus,
        )
    }

    fun listMyStudents(curatorLogin: String, groupId: Int? = null): List<CuratorStudentRow> {
        val curator = requireCurator(curatorLogin)
        val curatorId = curator.id ?: throw BusinessException(
            code = "CURATOR_ID_MISSING",
            userMessage = "Профиль куратора поврежден. Обратитесь к администратору.",
            status = HttpStatus.CONFLICT,
        )
        val groups = groupRepository.findAllByCuratorId(curatorId)
        val groupsById = groups.associateBy { it.id }
        val allowedGroupIds = groupsById.keys.filterNotNull().toSet()
        if (allowedGroupIds.isEmpty()) return emptyList()
        if (groupId != null && groupId !in allowedGroupIds) {
            throw BusinessException(
                code = "GROUP_ACCESS_DENIED",
                userMessage = "Группа недоступна куратору.",
                status = HttpStatus.FORBIDDEN,
            )
        }
        val targetGroupIds = if (groupId == null) allowedGroupIds else setOf(groupId)

        return userRepository.findAll()
            .asSequence()
            .filter { it.roles.contains(Role.STUDENT) }
            .filter { it.group?.id in targetGroupIds }
            .map {
                CuratorStudentRow(
                    userId = it.id!!,
                    fullName = "${it.surname} ${it.name} ${it.fatherName}",
                    groupId = it.group!!.id!!,
                    groupName = it.group!!.groupName,
                    studentCategory = it.studentCategory,
                    accountStatus = it.accountStatus,
                )
            }
            .sortedWith(compareBy({ it.groupName }, { it.fullName }))
            .toList()
    }

    @Transactional
    fun applyAbsence(
        curatorLogin: String,
        studentId: UUID,
        request: CuratorStudentAbsenceRequest
    ) {
        val curator = requireCurator(curatorLogin)
        val curatorId = curator.id ?: throw BusinessException(
            code = "CURATOR_ID_MISSING",
            userMessage = "Профиль куратора поврежден. Обратитесь к администратору.",
            status = HttpStatus.CONFLICT,
        )
        val student = userRepository.findById(studentId).orElseThrow {
            BusinessException(
                code = "STUDENT_NOT_FOUND",
                userMessage = "Студент не найден.",
                status = HttpStatus.NOT_FOUND,
            )
        }
        if (!student.roles.contains(Role.STUDENT)) {
            throw BusinessException(
                code = "TARGET_NOT_STUDENT",
                userMessage = "Можно менять питание только студенту.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        val groupId = student.group?.id ?: throw BusinessException(
            code = "STUDENT_GROUP_REQUIRED",
            userMessage = "Студент не привязан к группе.",
            status = HttpStatus.CONFLICT,
        )
        if (!groupRepository.existsByIdAndCuratorId(groupId, curatorId)) {
            throw BusinessException(
                code = "GROUP_ACCESS_DENIED",
                userMessage = "Можно менять питание только студентов своих групп.",
                status = HttpStatus.FORBIDDEN,
            )
        }
        if (student.accountStatus == AccountStatus.FROZEN_EXPELLED) {
            throw BusinessException(
                code = "STUDENT_FROZEN_EXPELLED",
                userMessage = "Студент отчислен и заморожен. Назначение питания запрещено.",
                status = HttpStatus.CONFLICT,
            )
        }

        val reasonType = request.noMealReasonType
        if (reasonType == NoMealReasonType.MISSING_ROSTER) {
            throw BusinessException(
                code = "NO_MEAL_REASON_INVALID",
                userMessage = "Причина «Куратор не заполнил табель» выставляется только системой после дедлайна.",
            )
        }

        val absenceFrom = request.absenceFrom
        val absenceTo = request.absenceTo
        if (absenceTo.isBefore(absenceFrom)) {
            throw BusinessException(
                code = "ABSENCE_RANGE_INVALID",
                userMessage = "Дата окончания периода отсутствия не может быть раньше даты начала.",
            )
        }
        if (reasonType == NoMealReasonType.OTHER && request.noMealReasonText.isNullOrBlank()) {
            throw BusinessException(
                code = "NO_MEAL_REASON_TEXT_REQUIRED",
                userMessage = "Для причины 'Иное' нужно заполнить текст причины.",
            )
        }

        val allDates = datesInRange(absenceFrom, absenceTo)
            .filter(rosterWeekPolicy::isWeekday)
        if (allDates.isEmpty()) {
            throw BusinessException(
                code = "ROSTER_WEEKEND_FORBIDDEN",
                userMessage = "Период отсутствия должен включать хотя бы один рабочий день Пн–Пт.",
            )
        }

        allDates.forEach { date ->
            if (!rosterWeekPolicy.isDateEditable(date)) {
                throw BusinessException(
                    code = "ROSTER_WEEK_LOCKED",
                    userMessage = "Период отсутствия можно выставлять только на следующую неделю и далее до дедлайна пятницы 12:00.",
                )
            }

            val existing = mealPermissionRepository.findByStudentAndDate(student, date)
            val permission = existing ?: MealPermissionEntity(
                date = date,
                student = student,
                assignedBy = curator,
                reason = "Общее основание",
                isBreakfastAllowed = false,
                isLunchAllowed = false,
            )
            permission.isBreakfastAllowed = false
            permission.isLunchAllowed = false
            permission.noMealReasonType = reasonType
            permission.noMealReasonText = request.noMealReasonText?.trim()?.ifBlank { null }
            permission.absenceFrom = absenceFrom
            permission.absenceTo = absenceTo
            permission.comment = request.comment?.trim()?.ifBlank { null }
            permission.reason = listOf(
                permission.comment,
                permission.noMealReasonText,
                noMealReasonTitleRu(reasonType)
            ).firstOrNull { !it.isNullOrBlank() } ?: "Общее основание"
            mealPermissionRepository.save(permission)
        }

        if (reasonType == NoMealReasonType.EXPELLED) {
            student.accountStatus = AccountStatus.FROZEN_EXPELLED
            student.expelledAt = rosterWeekPolicy.now()
            student.expelledBy = curator
            student.expelNote = request.comment?.trim()?.ifBlank { null }
            userRepository.save(student)
        }
    }

    private fun requireCurator(curatorLogin: String): UserEntity {
        val curator = userRepository.findByLogin(curatorLogin) ?: throw BusinessException(
            code = "CURATOR_NOT_FOUND",
            userMessage = "Куратор не найден.",
            status = HttpStatus.NOT_FOUND,
        )
        if (!curator.roles.contains(Role.CURATOR)) {
            throw BusinessException(
                code = "ROLE_FORBIDDEN",
                userMessage = "Действие доступно только куратору.",
                status = HttpStatus.FORBIDDEN,
            )
        }
        return curator
    }

    private fun datesInRange(from: LocalDate, to: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            result += cursor
            cursor = cursor.plusDays(1)
        }
        return result
    }

    private fun noMealReasonTitleRu(reasonType: NoMealReasonType): String = when (reasonType) {
        NoMealReasonType.EXPELLED -> "Отчислен"
        NoMealReasonType.SICK_LEAVE -> "Больничный"
        NoMealReasonType.OTHER -> "Иное"
        NoMealReasonType.MISSING_ROSTER -> "Куратор не заполнил табель"
    }
}
