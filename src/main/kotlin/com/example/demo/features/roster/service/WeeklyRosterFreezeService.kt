package com.example.demo.features.roster.service

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.CuratorWeekFillStatus
import com.example.demo.core.database.NoMealReasonType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.CuratorWeekAuditEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.entity.WeeklyReportSnapshotEntity
import com.example.demo.core.database.repository.CuratorWeekAuditRepository
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.database.repository.WeeklyReportSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class WeeklyRosterFreezeService(
    private val rosterWeekPolicy: RosterWeekPolicy,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val mealPermissionRepository: MealPermissionRepository,
    private val weeklyReportSnapshotRepository: WeeklyReportSnapshotRepository,
    private val curatorWeekAuditRepository: CuratorWeekAuditRepository,
) {

    @Transactional
    fun freezeNextWeekIfLocked(nowWeekReference: LocalDate = rosterWeekPolicy.today()): FreezeResult {
        val weekStart = rosterWeekPolicy.nextWeekStart(nowWeekReference)
        if (!rosterWeekPolicy.isLockedWeek(weekStart)) {
            return FreezeResult(
                weekStart = weekStart,
                auditsCreated = 0,
                missingRosterPermissionsCreated = 0,
                snapshotDays = 0,
                skipped = true,
            )
        }
        return freezeWeek(weekStart)
    }

    @Transactional
    fun freezeWeek(weekStart: LocalDate): FreezeResult {
        val weekDates = rosterWeekPolicy.weekDates(weekStart)
        val allGroups = groupRepository.findAllWithCurators()

        val activeStudentsByGroupId = allGroups.associate { group ->
            val students = userRepository.findAllByGroup(group)
                .asSequence()
                .filter { it.roles.contains(Role.STUDENT) }
                .filter { it.accountStatus == AccountStatus.ACTIVE }
                .distinctBy { it.id }
                .toList()
            (group.id ?: Int.MIN_VALUE) to students
        }

        val allActiveStudents = activeStudentsByGroupId.values
            .flatten()
            .distinctBy { it.id }

        val permissions = if (allActiveStudents.isEmpty()) {
            emptyList()
        } else {
            mealPermissionRepository.findAllByStudentInAndDateBetween(allActiveStudents, weekDates.first(), weekDates.last())
        }

        val permissionsByStudentDate = permissions
            .groupBy { (it.student.id ?: return@groupBy "missing") to it.date }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.id ?: Int.MIN_VALUE }!! }
            .toMutableMap()

        var missingCreated = 0
        var auditsCreated = 0

        allGroups.forEach { group ->
            val groupId = group.id ?: return@forEach
            val groupStudents = activeStudentsByGroupId[groupId].orEmpty()
            group.curators.forEach { curator ->
                val expectedCells = groupStudents.size * weekDates.size
                val filledCells = groupStudents.sumOf { student ->
                    val sid = student.id ?: return@sumOf 0
                    weekDates.count { date -> permissionsByStudentDate.containsKey(sid to date) }
                }
                val fillStatus = when {
                    expectedCells == 0 || filledCells == 0 -> CuratorWeekFillStatus.ZERO_FILL
                    filledCells >= expectedCells -> CuratorWeekFillStatus.FULL
                    else -> CuratorWeekFillStatus.PARTIAL
                }

                val existingAudit = curatorWeekAuditRepository.findByCuratorAndWeekStart(curator, weekStart)
                if (existingAudit == null) {
                    curatorWeekAuditRepository.save(
                        CuratorWeekAuditEntity(
                            curator = curator,
                            weekStart = weekStart,
                            filledCells = filledCells,
                            expectedCells = expectedCells,
                            fillStatus = fillStatus,
                            lockedAt = rosterWeekPolicy.now(),
                        )
                    )
                    auditsCreated++
                }

                for (student in groupStudents) {
                    val sid = student.id ?: continue
                    for (date in weekDates) {
                        val key = sid to date
                        if (permissionsByStudentDate.containsKey(key)) {
                            continue
                        }

                        val created = MealPermissionEntity(
                            date = date,
                            student = student,
                            assignedBy = curator,
                            reason = "MISSING_ROSTER",
                            isBreakfastAllowed = false,
                            isLunchAllowed = false,
                            noMealReasonType = NoMealReasonType.MISSING_ROSTER,
                            noMealReasonText = "Табель не заполнен куратором к дедлайну",
                            absenceFrom = null,
                            absenceTo = null,
                            comment = "Системная отметка после дедлайна",
                        )
                        val saved = mealPermissionRepository.save(created)
                        permissionsByStudentDate[key] = saved
                        missingCreated++
                    }
                }
            }
        }

        val allWeekPermissions = if (allActiveStudents.isEmpty()) {
            emptyList()
        } else {
            mealPermissionRepository.findAllByStudentInAndDateBetween(allActiveStudents, weekDates.first(), weekDates.last())
        }

        val latestByStudentDate = allWeekPermissions
            .groupBy { (it.student.id ?: return@groupBy "missing") to it.date }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.id ?: Int.MIN_VALUE }!! }

        weeklyReportSnapshotRepository.deleteAllByWeekStart(weekStart)
        weekDates.forEach { date ->
            val dayPerms = latestByStudentDate.values.filter { it.date == date }
            val breakfastCount = dayPerms.count { it.isBreakfastAllowed }
            val lunchCount = dayPerms.count { it.isLunchAllowed }
            val bothCount = dayPerms.count { it.isBreakfastAllowed && it.isLunchAllowed }

            weeklyReportSnapshotRepository.save(
                WeeklyReportSnapshotEntity(
                    weekStart = weekStart,
                    date = date,
                    breakfastCount = breakfastCount,
                    lunchCount = lunchCount,
                    bothCount = bothCount,
                    finalizedAt = rosterWeekPolicy.now(),
                )
            )
        }

        return FreezeResult(
            weekStart = weekStart,
            auditsCreated = auditsCreated,
            missingRosterPermissionsCreated = missingCreated,
            snapshotDays = weekDates.size,
            skipped = false,
        )
    }
}

data class FreezeResult(
    val weekStart: LocalDate,
    val auditsCreated: Int,
    val missingRosterPermissionsCreated: Int,
    val snapshotDays: Int,
    val skipped: Boolean,
)
