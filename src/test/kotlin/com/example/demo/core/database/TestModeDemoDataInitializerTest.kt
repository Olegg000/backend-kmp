package com.example.demo.core.database

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.MenuRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@Transactional
@ActiveProfiles(resolver = TestProfileResolver::class)
@TestPropertySource(
    properties = [
        "app.test-mode.enabled=true",
        "app.bootstrap-admin.enabled=false",
    ]
)
@DisplayName("TestModeDemoDataInitializer - включенный test mode")
class TestModeDemoDataInitializerTest {

    @Autowired
    private lateinit var initializer: TestModeDemoDataInitializer

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var mealPermissionRepository: MealPermissionRepository

    @Autowired
    private lateinit var mealTransactionRepository: MealTransactionRepository

    @Autowired
    private lateinit var menuRepository: MenuRepository

    @Autowired
    private lateinit var rosterWeekPolicy: RosterWeekPolicy

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `seed creates deterministic demo users and report scenarios`() {
        val demoLogins = demoLogins()
        demoLogins.forEach { login ->
            val user = userRepository.findByLogin(login)
            assertNotNull(user, "Должен существовать демо-пользователь $login")
            assertTrue(passwordEncoder.matches("password", user!!.passwordHash), "Пароль демо-пользователя $login")
        }

        val admin = userRepository.findByLogin("admin")!!
        assertTrue(admin.roles.contains(Role.ADMIN))
        assertTrue(admin.roles.contains(Role.REGISTRATOR))
        assertTrue(admin.roles.contains(Role.CHEF))
        assertTrue(admin.roles.contains(Role.CURATOR))
        assertTrue(admin.roles.contains(Role.STUDENT))
        val adminTodayPermission = mealPermissionRepository.findByStudentAndDate(admin, rosterWeekPolicy.today())
        assertNotNull(adminTodayPermission, "Админу в test mode должен выдаваться талон на текущую дату")
        assertTrue(adminTodayPermission!!.isBreakfastAllowed)
        assertTrue(adminTodayPermission.isLunchAllowed)

        val group101 = groupRepository.findByGroupName("Group-101")
        val group102 = groupRepository.findByGroupName("Group-102")
        assertNotNull(group101)
        assertNotNull(group102)
        assertFalse(group101!!.curators.isEmpty(), "Group-101 должен иметь куратора")

        val studentReady = userRepository.findByLogin("student_test")!!
        assertNotNull(studentReady.group, "student_test должен быть в группе")
        assertEquals("Group-101", studentReady.group!!.groupName)
        val studentReadyTodayPermission = mealPermissionRepository.findByStudentAndDate(studentReady, rosterWeekPolicy.today())
        assertNotNull(studentReadyTodayPermission, "student_test должен иметь талон на текущую дату")
        assertTrue(studentReadyTodayPermission!!.isBreakfastAllowed, "student_test должен иметь завтрак")
        assertTrue(studentReadyTodayPermission.isLunchAllowed, "student_test должен иметь обед")

        val studentSick = userRepository.findByLogin("stud_Group-101_2")!!
        val studentExpelled = userRepository.findByLogin("stud_Group-101_3")!!
        val studentOther = userRepository.findByLogin("stud_Group-101_4")!!
        val studentNoCuratorA = userRepository.findByLogin("stud_Group-102_1")!!
        val studentNoCuratorB = userRepository.findByLogin("stud_Group-102_2")!!

        assertEquals(AccountStatus.FROZEN_EXPELLED, studentExpelled.accountStatus)

        val (seedStart, seedEnd) = seedRange()
        val sickPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            listOf(studentSick),
            seedStart,
            seedEnd,
        )
        assertTrue(sickPermissions.any { it.noMealReasonType == NoMealReasonType.SICK_LEAVE })
        assertTrue(sickPermissions.any { it.absenceFrom != null && it.absenceTo != null })

        val expelledPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            listOf(studentExpelled),
            seedStart,
            seedEnd,
        )
        assertTrue(expelledPermissions.any { it.noMealReasonType == NoMealReasonType.EXPELLED })

        val otherPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            listOf(studentOther),
            seedStart,
            seedEnd,
        )
        assertTrue(otherPermissions.any { it.noMealReasonType == NoMealReasonType.OTHER && !it.noMealReasonText.isNullOrBlank() })

        val missingRosterGroupPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            listOf(studentNoCuratorA, studentNoCuratorB),
            seedStart,
            seedEnd,
        )
        assertTrue(missingRosterGroupPermissions.isEmpty(), "Для Group-102 табель в seed-диапазоне должен отсутствовать")

        val demoTransactions = mealTransactionRepository.findAllByTransactionHashStartingWith("demo_")
        assertTrue(demoTransactions.isNotEmpty(), "Должны быть demo_ транзакции")
        assertTrue(demoTransactions.all { it.transactionHash.startsWith("demo_") })
    }

    @Test
    fun `seed is idempotent on repeated run`() {
        val (seedStart, seedEnd) = seedRange()
        val demoStudents = demoStudentLogins().mapNotNull { userRepository.findByLogin(it) }
        val beforeUsers = demoLogins().count { userRepository.findByLogin(it) != null }
        val beforePermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            demoStudents,
            seedStart,
            seedEnd,
        ).size
        val beforeTransactions = mealTransactionRepository.findAllByTransactionHashStartingWith("demo_").size
        val beforeMenu = countSeededMenuRows(seedStart, seedEnd)

        val admin = userRepository.findByLogin("admin")!!
        val today = rosterWeekPolicy.today()
        val beforeAdminTodayPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            listOf(admin),
            today,
            today,
        )
        assertEquals(1, beforeAdminTodayPermissions.size)
        val beforeAdminTodayPermission = beforeAdminTodayPermissions.single()
        assertNotNull(beforeAdminTodayPermission)
        assertTrue(beforeAdminTodayPermission.isBreakfastAllowed)
        assertTrue(beforeAdminTodayPermission.isLunchAllowed)

        initializer.run(DefaultApplicationArguments())

        val afterUsers = demoLogins().count { userRepository.findByLogin(it) != null }
        val afterPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            demoStudents,
            seedStart,
            seedEnd,
        ).size
        val afterTransactions = mealTransactionRepository.findAllByTransactionHashStartingWith("demo_").size
        val afterMenu = countSeededMenuRows(seedStart, seedEnd)
        val afterAdminTodayPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            listOf(admin),
            today,
            today,
        )

        assertEquals(beforeUsers, afterUsers)
        assertEquals(beforePermissions, afterPermissions)
        assertEquals(beforeTransactions, afterTransactions)
        assertEquals(beforeMenu, afterMenu)
        assertEquals(1, afterAdminTodayPermissions.size)
        val afterAdminTodayPermission = afterAdminTodayPermissions.single()
        assertTrue(afterAdminTodayPermission.isBreakfastAllowed)
        assertTrue(afterAdminTodayPermission.isLunchAllowed)
    }

    @Test
    fun `seed preserves existing curator links on rerun`() {
        val group101 = groupRepository.findByGroupName("Group-101") ?: error("Group-101 must exist")
        val group102 = groupRepository.findByGroupName("Group-102") ?: error("Group-102 must exist")

        val extraCurator101 = userRepository.save(
            UserEntity(
                login = "curator_extra_101",
                passwordHash = passwordEncoder.encode("password"),
                roles = mutableSetOf(Role.CURATOR),
                name = "Ирина",
                surname = "Тестова",
                fatherName = "Петровна",
            )
        )
        val extraCurator102 = userRepository.save(
            UserEntity(
                login = "curator_extra_102",
                passwordHash = passwordEncoder.encode("password"),
                roles = mutableSetOf(Role.CURATOR),
                name = "Олег",
                surname = "Проверкин",
                fatherName = "Иванович",
            )
        )

        group101.curators.add(extraCurator101)
        group102.curators.add(extraCurator102)
        groupRepository.save(group101)
        groupRepository.save(group102)

        initializer.run(DefaultApplicationArguments())

        val group101After = groupRepository.findByGroupName("Group-101") ?: error("Group-101 must exist after rerun")
        val group102After = groupRepository.findByGroupName("Group-102") ?: error("Group-102 must exist after rerun")

        assertTrue(group101After.curators.any { it.id == extraCurator101.id }, "Дополнительный куратор Group-101 не должен удаляться")
        assertTrue(group102After.curators.any { it.id == extraCurator102.id }, "Дополнительный куратор Group-102 не должен удаляться")
        assertTrue(
            group101After.curators.any { it.login == "curator_Group-101" },
            "Демо-куратор Group-101 должен оставаться привязанным",
        )
    }

    private fun seedRange(): Pair<LocalDate, LocalDate> {
        val currentWeek = rosterWeekPolicy.weekStart(rosterWeekPolicy.today())
        val previousWeek = currentWeek.minusWeeks(1)
        return previousWeek to currentWeek.plusDays(4)
    }

    private fun countSeededMenuRows(startDate: LocalDate, endDate: LocalDate): Int {
        var count = 0
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            count += menuRepository.findAllByDateAndLocationIgnoreCase(cursor, "Столовая 1")
                .count { it.name == "Завтрак тестовый" || it.name == "Обед тестовый" }
            cursor = cursor.plusDays(1)
        }
        return count
    }

    private fun demoStudentLogins(): List<String> = listOf(
        "stud_Group-101_1",
        "stud_Group-101_2",
        "stud_Group-101_3",
        "stud_Group-101_4",
        "stud_Group-101_5",
        "stud_Group-102_1",
        "stud_Group-102_2",
        "student_test",
    )

    private fun demoLogins(): List<String> = listOf(
        "admin",
        "chef_main",
        "registrator",
        "curator_Group-101",
    ) + demoStudentLogins()
}

@SpringBootTest
@Transactional
@ActiveProfiles(resolver = TestProfileResolver::class)
@TestPropertySource(
    properties = [
        "app.test-mode.enabled=false",
        "app.bootstrap-admin.enabled=false",
    ]
)
@DisplayName("TestModeDemoDataInitializer - выключенный test mode")
class TestModeDemoDataInitializerDisabledTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mealTransactionRepository: MealTransactionRepository

    @Test
    fun `seed should not create demo data when disabled`() {
        assertNull(userRepository.findByLogin("admin"))
        assertNull(userRepository.findByLogin("chef_main"))
        assertNull(userRepository.findByLogin("registrator"))
        assertNull(userRepository.findByLogin("curator_Group-101"))
        assertNull(userRepository.findByLogin("student_test"))
        assertTrue(mealTransactionRepository.findAllByTransactionHashStartingWith("demo_").isEmpty())
    }
}
