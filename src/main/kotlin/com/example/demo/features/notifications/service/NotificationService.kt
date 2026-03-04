package com.example.demo.features.notifications.service

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.NotificationDispatchLogEntity
import com.example.demo.core.database.entity.NotificationEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.repository.CuratorWeekSubmissionRepository
import com.example.demo.core.database.repository.ChefWeekConfirmationRepository
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.NotificationDispatchLogRepository
import com.example.demo.core.database.repository.NotificationRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.notifications.dto.NotificationDto
import com.example.demo.features.notifications.dto.NotificationPageDto
import com.example.demo.features.notifications.dto.RosterDeadlineStatusDto
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class NotificationService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository,
    private val notificationRepository: NotificationRepository,
    private val weekSubmissionRepository: CuratorWeekSubmissionRepository,
    private val chefWeekConfirmationRepository: ChefWeekConfirmationRepository,
    private val dispatchLogRepository: NotificationDispatchLogRepository,
    private val rosterWeekPolicy: RosterWeekPolicy,
    private val pushDispatchServiceProvider: ObjectProvider<PushDispatchService>,
) {
    private val humanDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")


    fun checkCuratorRosterStatus(curatorLogin: String): RosterDeadlineStatusDto {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw BusinessException(
                code = "CURATOR_NOT_FOUND",
                userMessage = "Куратор не найден",
                status = HttpStatus.NOT_FOUND,
            )
        return buildCuratorDeadlineStatus(curator)
    }

    fun getUnreadCount(login: String): Long {
        val user = userRepository.findByLogin(login) ?: return 0
        return notificationRepository.countByUserAndIsReadFalse(user)
    }

    fun getUserNotificationsPage(login: String, cursor: Long?, limit: Int): NotificationPageDto {
        val user = userRepository.findByLogin(login) ?: return NotificationPageDto(emptyList(), null, false)

        val sorted = notificationRepository.findAllByUserOrderByCreatedAtDesc(user)
            .sortedByDescending { it.id ?: Long.MIN_VALUE }

        val filtered = if (cursor == null) sorted else sorted.filter { (it.id ?: Long.MIN_VALUE) < cursor }
        val pageItems = filtered.take(limit)
        val nextCursor = pageItems.lastOrNull()?.id
        val hasMore = filtered.size > pageItems.size

        return NotificationPageDto(
            items = pageItems.map {
                NotificationDto(
                    id = it.id!!,
                    title = it.title,
                    message = it.message,
                    isRead = it.isRead,
                    createdAt = it.createdAt.toString()
                )
            },
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    @Transactional
    fun markAsRead(login: String, notificationId: Long) {
        val user = userRepository.findByLogin(login) ?: throw BusinessException(
            code = "USER_NOT_FOUND",
            userMessage = "Пользователь не найден",
            status = HttpStatus.NOT_FOUND,
        )
        val notification = notificationRepository.findById(notificationId).orElseThrow {
            BusinessException(
                code = "NOTIFICATION_NOT_FOUND",
                userMessage = "Уведомление не найдено",
                status = HttpStatus.NOT_FOUND,
            )
        }
        if (notification.user.id == user.id) {
            notification.isRead = true
            notificationRepository.save(notification)
        }
    }

    @Transactional
    fun markAsReadBatch(login: String, ids: List<Long>) {
        if (ids.isEmpty()) return
        val user = userRepository.findByLogin(login) ?: throw BusinessException(
            code = "USER_NOT_FOUND",
            userMessage = "Пользователь не найден",
            status = HttpStatus.NOT_FOUND,
        )
        ids.forEach { id ->
            val notification = notificationRepository.findById(id).orElse(null) ?: return@forEach
            if (notification.user.id == user.id && !notification.isRead) {
                notification.isRead = true
                notificationRepository.save(notification)
            }
        }
    }

    @Transactional
    fun sendNotification(
        user: UserEntity,
        title: String,
        message: String,
        route: String? = null,
        type: String? = null,
    ) {
        val notification = NotificationEntity().apply {
            this.user = user
            this.title = title
            this.message = message
        }
        notificationRepository.save(notification)
        val pushData = buildMap {
            if (!route.isNullOrBlank()) put("route", route)
            if (!type.isNullOrBlank()) put("type", type)
        }
        pushDispatchServiceProvider.ifAvailable?.dispatchToUser(
            user = user,
            title = title,
            body = message,
            data = pushData,
        )
    }

    @Transactional
    fun sendCuratorDailyReminderIfNeeded(curator: UserEntity): Boolean {
        if (!curator.roles.contains(Role.CURATOR)) return false

        val weekStart = rosterWeekPolicy.nextWeekStart()
        val status = buildCuratorDeadlineStatus(curator)
        if (status.isSubmitted) return false

        val bucketKey = "daily:${rosterWeekPolicy.today()}:$weekStart"
        return sendNotificationOnce(
            user = curator,
            type = "CURATOR_DAILY_DEADLINE_REMINDER",
            bucketKey = bucketKey,
            title = "НАПОМИНАНИЕ О ТАБЕЛЕ",
            message = "Заполните табель на неделю ${formatDate(weekStart)}. Дедлайн: до пятницы 12:00.",
            route = "roster",
        )
    }

    @Transactional
    fun sendCuratorPostSubmitCheckInIfNeeded(curator: UserEntity): Boolean {
        if (!curator.roles.contains(Role.CURATOR)) return false
        val weekStart = rosterWeekPolicy.nextWeekStart()
        val status = buildCuratorDeadlineStatus(curator)
        if (!status.isSubmitted || status.isLocked) return false

        val now = rosterWeekPolicy.now()
        val dayBucket = now.toLocalDate().toEpochDay() / 2
        val bucketKey = "post-submit:$dayBucket:$weekStart"
        return sendNotificationOnce(
            user = curator,
            type = "CURATOR_POST_SUBMIT_CHECKIN",
            bucketKey = bucketKey,
            title = "ТАБЕЛЬ ПРИНЯТ",
            message = "Табель на неделю ${formatDate(weekStart)} заполнен. Проверьте еще раз, что все данные верны до пятницы 12:00.",
            route = "roster",
        )
    }

    @Transactional
    fun sendCuratorHourlyReminderIfZeroFill(curator: UserEntity): Boolean {
        if (!curator.roles.contains(Role.CURATOR)) return false

        val now = rosterWeekPolicy.now()
        if (now.dayOfWeek.value != java.time.DayOfWeek.FRIDAY.value) return false
        if (now.hour >= 12) return false

        val weekStart = rosterWeekPolicy.nextWeekStart(now.toLocalDate())
        if (!hasZeroFillForWeek(curator, weekStart)) return false

        val bucketKey = "hourly:${now.toLocalDate()}:${now.hour}:$weekStart"
        return sendNotificationOnce(
            user = curator,
            type = "CURATOR_FRIDAY_HOURLY_ZERO_FILL",
            bucketKey = bucketKey,
            title = "СРОЧНО: ЗАПОЛНИТЕ ТАБЕЛЬ",
            message = "На неделю ${formatDate(weekStart)} пока нет ни одной записи. Дедлайн сегодня до 12:00.",
            route = "roster",
        )
    }

    @Transactional
    fun notifyChefsWeeklyReportConfirmWindow(
        weekStart: LocalDate,
        now: LocalDateTime = rosterWeekPolicy.now(),
    ): Int {
        val chefs = userRepository.findAllByRoleAndAccountStatus(Role.CHEF, AccountStatus.ACTIVE)
        var sentCount = 0
        chefs.forEach { chef ->
            if (!rosterWeekPolicy.isChefConfirmationWindowOpen(weekStart, now)) return@forEach

            val alreadyConfirmed = hasChefConfirmedForWeek(chef, weekStart)
            if (alreadyConfirmed) return@forEach

            val bucketSuffix = when (now.dayOfWeek) {
                DayOfWeek.FRIDAY -> "fri-12"
                DayOfWeek.SATURDAY -> "sat-07"
                DayOfWeek.SUNDAY -> "sun-07"
                else -> "other"
            }
            val sent = sendNotificationOnce(
                user = chef,
                type = "CHEF_WEEKLY_REPORT_CONFIRM_REMINDER",
                bucketKey = "chef-confirm:$weekStart:$bucketSuffix",
                title = "ПОДТВЕРДИТЕ НЕДЕЛЬНЫЙ ОТЧЕТ",
                message = "Подтвердите недельный отчет на ${formatDate(weekStart)}. Это нужно сделать до понедельника 00:00.",
                route = "weekly_report",
            )
            if (sent) sentCount++
        }
        return sentCount
    }

    @Transactional
    fun notifyStudentsMorningKeyReminder(date: LocalDate = rosterWeekPolicy.today()): Int {
        val students = userRepository.findAllByRoleAndAccountStatus(Role.STUDENT, AccountStatus.ACTIVE)
        var sentCount = 0
        students.forEach { student ->
            val sent = sendNotificationOnce(
                user = student,
                type = "STUDENT_MORNING_KEYS_REMINDER",
                bucketKey = "student-keys:$date",
                title = "ОБНОВИТЕ КЛЮЧИ",
                message = "Зайдите в приложение и обновите ключи на сегодня.",
                route = "settings",
            )
            if (sent) sentCount++
        }
        return sentCount
    }

    @Transactional
    fun notifyChefsMorningKeyReminder(date: LocalDate = rosterWeekPolicy.today()): Int {
        val chefs = userRepository.findAllByRoleAndAccountStatus(Role.CHEF, AccountStatus.ACTIVE)
        var sentCount = 0
        chefs.forEach { chef ->
            val sent = sendNotificationOnce(
                user = chef,
                type = "CHEF_MORNING_KEYS_REMINDER",
                bucketKey = "chef-keys:$date",
                title = "ОБНОВИТЕ КЛЮЧИ СТУДЕНТОВ",
                message = "Перед сменой зайдите в приложение и скачайте актуальные ключи и разрешения.",
                route = "scanner",
            )
            if (sent) sentCount++
        }
        return sentCount
    }

    fun isWeekSubmitted(curator: UserEntity, weekStart: java.time.LocalDate): Boolean {
        return weekSubmissionRepository.findByCuratorAndWeekStart(curator, weekStart) != null
    }

    fun hasZeroFillForNextWeek(curator: UserEntity): Boolean {
        val nextWeek = rosterWeekPolicy.nextWeekStart()
        return hasZeroFillForWeek(curator, nextWeek)
    }

    fun hasChefConfirmedForWeek(chef: UserEntity, weekStart: LocalDate): Boolean {
        return chefWeekConfirmationRepository.findByChefAndWeekStart(chef, weekStart) != null
    }

    private fun hasZeroFillForWeek(curator: UserEntity, weekStart: java.time.LocalDate): Boolean {
        val curatorId = curator.id ?: return true
        val groups = groupRepository.findAllByCuratorId(curatorId)
        if (groups.isEmpty()) return true
        return hasZeroFillForWeek(groups, weekStart)
    }

    private fun hasZeroFillForWeek(groups: List<GroupEntity>, weekStart: java.time.LocalDate): Boolean {
        val weekDates = rosterWeekPolicy.weekDates(weekStart)
        val count = permissionRepository.countByGroupsAndDateRange(groups, weekDates.first(), weekDates.last())
        return count == 0L
    }

    private fun buildCuratorDeadlineStatus(curator: UserEntity): RosterDeadlineStatusDto {
        val weekStart = rosterWeekPolicy.nextWeekStart()
        val cutoff = rosterWeekPolicy.deadlineForWeek(weekStart)
        val isLocked = !rosterWeekPolicy.now().isBefore(cutoff)
        val curatorId = curator.id
        val groups = if (curatorId != null) groupRepository.findAllByCuratorId(curatorId) else emptyList()
        if (groups.isEmpty()) {
            return RosterDeadlineStatusDto(
                cutoffDateTime = cutoff.toString(),
                weekStart = weekStart.toString(),
                isSubmitted = false,
                isLocked = isLocked,
                severity = "INFO",
                needsReminder = false,
                daysUntilDeadline = 0,
                deadlineDate = cutoff.toLocalDate().toString(),
                reason = "Куратор не привязан к группам.",
                deadlineHuman = humanDeadline(weekStart),
                actionHint = "Привяжите куратора к группе, чтобы работать с табелем.",
            )
        }
        val isSubmitted = isWeekSubmitted(curator, weekStart)
        val zeroFill = hasZeroFillForWeek(groups, weekStart)

        val daysLeft = ChronoUnit.DAYS
            .between(rosterWeekPolicy.today(), cutoff.toLocalDate())
            .toInt()
            .coerceAtLeast(0)

        val severity = when {
            isSubmitted -> "INFO"
            isLocked -> "CRITICAL"
            zeroFill -> "HIGH"
            else -> "MEDIUM"
        }

        val reason = when {
            isSubmitted -> "Табель на следующую неделю заполнен."
            isLocked -> "Дедлайн прошел: следующая неделя заблокирована для редактирования."
            zeroFill -> "На следующую неделю нет ни одной записи табеля."
            else -> "Табель заполнен не полностью."
        }

        return RosterDeadlineStatusDto(
            cutoffDateTime = cutoff.toString(),
            weekStart = weekStart.toString(),
            isSubmitted = isSubmitted,
            isLocked = isLocked,
            severity = severity,
            needsReminder = zeroFill,
            daysUntilDeadline = daysLeft,
            deadlineDate = cutoff.toLocalDate().toString(),
            reason = reason,
            deadlineHuman = humanDeadline(weekStart),
            actionHint = when {
                isLocked -> "Дедлайн уже прошел. Следующая неделя закрыта для редактирования."
                isSubmitted -> "Проверяйте корректность данных до пятницы 12:00."
                else -> "Заполните табель до пятницы 12:00."
            },
        )
    }

    private fun humanDeadline(weekStart: LocalDate): String {
        val deadlineDate = rosterWeekPolicy.deadlineForWeek(weekStart).toLocalDate()
        return "до пятницы 12:00, ${formatDate(deadlineDate)}"
    }

    private fun formatDate(value: LocalDate): String = value.format(humanDateFormatter)

    private fun sendNotificationOnce(
        user: UserEntity,
        type: String,
        bucketKey: String,
        title: String,
        message: String,
        route: String? = null,
    ): Boolean {
        if (dispatchLogRepository.existsByUserAndTypeAndBucketKey(user, type, bucketKey)) {
            return false
        }

        return try {
            sendNotification(user, title, message, route = route, type = type)
            dispatchLogRepository.save(
                NotificationDispatchLogEntity(
                    user = user,
                    type = type,
                    bucketKey = bucketKey,
                    sentAt = rosterWeekPolicy.now(),
                )
            )
            true
        } catch (_: DataIntegrityViolationException) {
            false
        }
    }
}
