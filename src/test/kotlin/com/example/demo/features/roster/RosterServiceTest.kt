package com.example.demo.features.roster

import com.example.demo.config.TestProfileResolver
import com.example.demo.config.TimeConfig
import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.NoMealReasonType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.notifications.service.NotificationService
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.roster.dto.UpdateRosterRequest
import com.example.demo.features.roster.service.RosterService
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@DataJpaTest
@Import(RosterService::class, RosterWeekPolicy::class, NotificationService::class, TimeConfig::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("RosterService - Управление табелем питания")
class RosterServiceTest {

    @Autowired
    private lateinit var rosterService: RosterService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    private lateinit var curator: UserEntity
    private lateinit var student1: UserEntity
    private lateinit var student2: UserEntity
    private lateinit var group: GroupEntity

    @BeforeEach
    fun setup() {
        group = groupRepository.save(GroupEntity(groupName = "ИСП-21"))

        curator = userRepository.save(
            UserEntity(
                login = "curator1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Руководителевна",
                group = group
            )
        )

        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        student1 = userRepository.save(
            UserEntity(
                login = "student1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
                group = group,
                studentCategory = StudentCategory.SVO
            )
        )

        student2 = userRepository.save(
            UserEntity(
                login = "student2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Петр",
                surname = "Обучаемый",
                fatherName = "Педагогович",
                group = group,
                studentCategory = StudentCategory.SVO
            )
        )
    }

    @Test
    @DisplayName("Куратор видит всех студентов своей группы на следующей неделе")
    fun `getRosterForGroup should return all students in curator group`() {
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        val roster = rosterService.getRosterForGroup(curator.login, nextMonday)

        assertEquals(2, roster.size)
        roster.forEach { row ->
            assertEquals(5, row.days.size)
            assertTrue(row.days.all { it.date.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY })
        }
    }

    @Test
    @DisplayName("Назначение питания на следующую неделю сохраняется")
    fun `updateRoster should save permissions correctly`() {
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(
                DayPermissionDto(nextMonday, isBreakfast = true, isLunch = false, reason = "Учебный день"),
            )
        )

        rosterService.updateRoster(request, curator.login)

        val saved = permissionRepository.findByStudentAndDate(student1, nextMonday)
        assertNotNull(saved)
        assertTrue(saved!!.isBreakfastAllowed)
        assertFalse(saved.isLunchAllowed)
    }

    @Test
    @DisplayName("Для false/false причина обязательна")
    fun `all false without reason type should fail`() {
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(
                DayPermissionDto(nextMonday, isBreakfast = false, isLunch = false)
            )
        )

        val ex = assertThrows(BusinessException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }

        assertEquals("NO_MEAL_REASON_REQUIRED", ex.code)
    }

    @Test
    @DisplayName("MISSING_ROSTER нельзя выставлять вручную")
    fun `missing roster reason should be forbidden for curator`() {
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(
                DayPermissionDto(
                    date = nextMonday,
                    isBreakfast = false,
                    isLunch = false,
                    noMealReasonType = NoMealReasonType.MISSING_ROSTER,
                )
            )
        )

        val ex = assertThrows(BusinessException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }

        assertEquals("NO_MEAL_REASON_INVALID", ex.code)
    }

    @Test
    @DisplayName("Для причины OTHER обязателен диапазон и текст")
    fun `other reason requires text and range`() {
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(
                DayPermissionDto(
                    date = nextMonday,
                    isBreakfast = false,
                    isLunch = false,
                    noMealReasonType = NoMealReasonType.OTHER,
                    noMealReasonText = "Дистант",
                    absenceFrom = nextMonday,
                    absenceTo = nextMonday,
                )
            )
        )

        rosterService.updateRoster(request, curator.login)

        val saved = permissionRepository.findByStudentAndDate(student1, nextMonday)
        assertNotNull(saved)
        assertEquals(NoMealReasonType.OTHER, saved!!.noMealReasonType)
        assertEquals("Дистант", saved.noMealReasonText)
    }

    @Test
    @DisplayName("Выходные запрещены")
    fun `weekend should be forbidden`() {
        val nextSaturday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(
                DayPermissionDto(nextSaturday, isBreakfast = true, isLunch = false, reason = "Тест")
            )
        )

        val ex = assertThrows(BusinessException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }

        assertEquals("ROSTER_WEEKEND_FORBIDDEN", ex.code)
    }

    @Test
    @DisplayName("Нельзя редактировать прошлую неделю")
    fun `past week should be forbidden`() {
        val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(
                DayPermissionDto(thisMonday, isBreakfast = true, isLunch = false, reason = "Тест")
            )
        )

        val ex = assertThrows(BusinessException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }

        assertEquals("ROSTER_ONLY_NEXT_WEEK_OR_LATER", ex.code)
    }

    @Test
    @DisplayName("Текущая неделя недоступна для чтения при выключенном test-mode")
    fun `current week roster read should be forbidden when test mode disabled`() {
        val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val ex = assertThrows(BusinessException::class.java) {
            rosterService.getRosterForGroup(curator.login, thisMonday)
        }

        assertEquals("ROSTER_ONLY_NEXT_WEEK_OR_LATER", ex.code)
    }

    @Test
    @DisplayName("Табель возвращает отчисленного студента со статусом FROZEN_EXPELLED")
    fun `get roster should include expelled student with account status`() {
        student2.accountStatus = AccountStatus.FROZEN_EXPELLED
        userRepository.save(student2)
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        val roster = rosterService.getRosterForGroup(curator.login, nextMonday)
        val expelledRow = roster.firstOrNull { it.studentId == student2.id }

        assertNotNull(expelledRow)
        assertEquals(AccountStatus.FROZEN_EXPELLED, expelledRow?.accountStatus)
    }

    @Test
    @DisplayName("Нельзя обновить табель для отчисленного студента")
    fun `update roster for expelled student should be forbidden`() {
        student1.accountStatus = AccountStatus.FROZEN_EXPELLED
        userRepository.save(student1)
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(
                DayPermissionDto(nextMonday, isBreakfast = true, isLunch = false, reason = "Тест")
            )
        )

        val ex = assertThrows(BusinessException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }

        assertEquals("STUDENT_FROZEN_EXPELLED", ex.code)
    }
}
