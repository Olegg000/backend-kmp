package com.example.demo.features.qr.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.ChefWeekConfirmationEntity
import com.example.demo.core.database.repository.ChefWeekConfirmationRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.qr.dto.StudentKeyDto
import com.example.demo.features.qr.dto.ChefWeeklyReportDayDto
import com.example.demo.features.qr.dto.ChefWeeklyReportDto
import com.example.demo.features.qr.dto.StudentPermissionDto
import com.example.demo.features.roster.service.RosterWeekPolicy
import com.example.demo.features.roster.service.WeeklyRosterFreezeService
import com.example.demo.core.database.repository.WeeklyReportSnapshotRepository
import com.example.demo.core.exception.BusinessException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Service
class ChefDataService(
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository,
    private val weeklyReportSnapshotRepository: WeeklyReportSnapshotRepository,
    private val chefWeekConfirmationRepository: ChefWeekConfirmationRepository,
    private val rosterWeekPolicy: RosterWeekPolicy,
    private val weeklyRosterFreezeService: WeeklyRosterFreezeService,
    private val businessClock: Clock,
) {

    /**
     * Возвращает публичные ключи всех студентов, у которых есть ключи.
     * Повар скачивает их для оффлайн ECDSA верификации.
     */
    fun getAllStudentKeys(): List<StudentKeyDto> {
        return userRepository.findAllStudentsWithPublicKey()
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
        val today = LocalDate.now(businessClock)
        val permissions = permissionRepository.findAllByDate(today)

        return permissions.map { perm ->
            val student = perm.student
            StudentPermissionDto(
                studentId = student.id!!,
                name = student.name,
                surname = student.surname,
                breakfast = perm.isBreakfastAllowed,
                lunch = perm.isLunchAllowed
            )
        }
    }

    fun getWeeklyReport(currentLogin: String, weekStart: LocalDate): ChefWeeklyReportDto {
        val currentUser = userRepository.findByLogin(currentLogin)
            ?: throw RuntimeException("Пользователь не найден")

        val normalizedWeekStart = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (normalizedWeekStart != weekStart) {
            throw BusinessException(
                code = "WEEK_START_MUST_BE_MONDAY",
                userMessage = "weekStart должен указывать на понедельник",
                status = HttpStatus.BAD_REQUEST,
            )
        }

        val today = rosterWeekPolicy.today()
        val nextWeekStart = rosterWeekPolicy.nextWeekStart(today)
        var snapshotRows = weeklyReportSnapshotRepository.findAllByWeekStartOrderByDateAsc(normalizedWeekStart)

        val useSnapshot = when {
            normalizedWeekStart == nextWeekStart && rosterWeekPolicy.isLockedWeek(normalizedWeekStart) -> {
                if (snapshotRows.isEmpty()) {
                    weeklyRosterFreezeService.freezeWeek(normalizedWeekStart)
                    snapshotRows = weeklyReportSnapshotRepository.findAllByWeekStartOrderByDateAsc(normalizedWeekStart)
                }
                true
            }
            normalizedWeekStart.isBefore(nextWeekStart) -> snapshotRows.isNotEmpty()
            else -> false
        }

        val days = if (useSnapshot) {
            buildDaysFromSnapshot(normalizedWeekStart, snapshotRows)
        } else {
            buildDaysFromLivePermissions(normalizedWeekStart)
        }
        val totalBreakfast = days.sumOf { it.breakfastCount }
        val totalLunch = days.sumOf { it.lunchCount }
        val totalBoth = days.sumOf { it.bothCount }

        val confirmation = if (currentUser.roles.contains(Role.CHEF)) {
            chefWeekConfirmationRepository.findByChefAndWeekStart(currentUser, normalizedWeekStart)
        } else {
            null
        }

        val windowStart = rosterWeekPolicy.chefConfirmationWindowStart(normalizedWeekStart)
        val windowEnd = rosterWeekPolicy.chefConfirmationWindowEnd(normalizedWeekStart)
        val canConfirmNow = currentUser.roles.contains(Role.CHEF) &&
            confirmation == null &&
            normalizedWeekStart == rosterWeekPolicy.nextWeekStart(rosterWeekPolicy.today()) &&
            rosterWeekPolicy.isChefConfirmationWindowOpen(normalizedWeekStart)

        return ChefWeeklyReportDto(
            weekStart = normalizedWeekStart,
            days = days,
            totalBreakfastCount = totalBreakfast,
            totalLunchCount = totalLunch,
            totalBothCount = totalBoth,
            confirmed = confirmation != null,
            confirmedAt = confirmation?.confirmedAt,
            canConfirmNow = canConfirmNow,
            confirmWindowStart = windowStart,
            confirmWindowEnd = windowEnd,
            confirmWindowHint = "Подтверждение доступно с пятницы 12:00 до понедельника 00:00.",
        )
    }

    private fun buildDaysFromSnapshot(
        weekStart: LocalDate,
        snapshotRows: List<com.example.demo.core.database.entity.WeeklyReportSnapshotEntity>
    ): List<ChefWeeklyReportDayDto> {
        val snapshotByDate = snapshotRows.associateBy { it.date }
        return rosterWeekPolicy.weekDates(weekStart).map { date ->
            val row = snapshotByDate[date]
            ChefWeeklyReportDayDto(
                date = date,
                breakfastCount = row?.breakfastCount ?: 0,
                lunchCount = row?.lunchCount ?: 0,
                bothCount = row?.bothCount ?: 0,
            )
        }
    }

    private fun buildDaysFromLivePermissions(weekStart: LocalDate): List<ChefWeeklyReportDayDto> {
        val weekDates = rosterWeekPolicy.weekDates(weekStart)
        val permissions = permissionRepository.findAllByDateBetween(weekDates.first(), weekDates.last())

        val latestByStudentDate = mutableMapOf<Pair<UUID, LocalDate>, com.example.demo.core.database.entity.MealPermissionEntity>()
        permissions.forEach { permission ->
            val studentId = permission.student.id ?: return@forEach
            val key = studentId to permission.date
            val previous = latestByStudentDate[key]
            if (previous == null || (permission.id ?: Int.MIN_VALUE) > (previous.id ?: Int.MIN_VALUE)) {
                latestByStudentDate[key] = permission
            }
        }

        val dayPermissions = latestByStudentDate.values.groupBy { it.date }
        return weekDates.map { date ->
            val rows = dayPermissions[date].orEmpty()
            ChefWeeklyReportDayDto(
                date = date,
                breakfastCount = rows.count { it.isBreakfastAllowed },
                lunchCount = rows.count { it.isLunchAllowed },
                bothCount = rows.count { it.isBreakfastAllowed && it.isLunchAllowed },
            )
        }
    }

    fun confirmWeeklyReport(currentLogin: String, weekStart: LocalDate) {
        val currentUser = userRepository.findByLogin(currentLogin)
            ?: throw RuntimeException("Пользователь не найден")
        if (!currentUser.roles.contains(Role.CHEF)) {
            throw BusinessException(
                code = "CHEF_ROLE_REQUIRED",
                userMessage = "Подтверждение отчета доступно только повару",
                status = HttpStatus.FORBIDDEN,
            )
        }

        val normalizedWeekStart = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val now = rosterWeekPolicy.now()
        val expectedWeekStart = rosterWeekPolicy.nextWeekStart(now.toLocalDate())
        if (normalizedWeekStart != expectedWeekStart) {
            throw BusinessException(
                code = "CHEF_CONFIRM_INVALID_WEEK",
                userMessage = "Можно подтверждать только отчет на следующую неделю.",
                status = HttpStatus.CONFLICT,
            )
        }

        val windowStart = rosterWeekPolicy.chefConfirmationWindowStart(normalizedWeekStart)
        val windowEnd = rosterWeekPolicy.chefConfirmationWindowEnd(normalizedWeekStart)
        if (now.isBefore(windowStart)) {
            throw BusinessException(
                code = "CHEF_CONFIRM_TOO_EARLY",
                userMessage = "Подтверждение доступно только после пятницы 12:00.",
                status = HttpStatus.CONFLICT,
            )
        }
        if (!now.isBefore(windowEnd)) {
            throw BusinessException(
                code = "CHEF_CONFIRM_WINDOW_CLOSED",
                userMessage = "Окно подтверждения закрыто. Подтверждение доступно только до понедельника 00:00.",
                status = HttpStatus.CONFLICT,
            )
        }

        val existing = chefWeekConfirmationRepository.findByChefAndWeekStart(currentUser, normalizedWeekStart)
        if (existing != null) {
            return
        }
        chefWeekConfirmationRepository.save(
            ChefWeekConfirmationEntity(
                chef = currentUser,
                weekStart = normalizedWeekStart,
                confirmedAt = rosterWeekPolicy.now(),
            )
        )
    }
}
