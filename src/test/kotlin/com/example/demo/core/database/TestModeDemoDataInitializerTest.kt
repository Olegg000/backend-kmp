package com.example.demo.core.database

import com.example.demo.config.TestProfileResolver
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

        val group101 = groupRepository.findByGroupName("Group-101")
        val group102 = groupRepository.findByGroupName("Group-102")
        assertNotNull(group101)
        assertNotNull(group102)
        assertFalse(group101!!.curators.isEmpty(), "Group-101 должен иметь куратора")
        assertTrue(group102!!.curators.isEmpty(), "Group-102 должен быть без куратора")

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

        initializer.run(DefaultApplicationArguments(emptyArray()))

        val afterUsers = demoLogins().count { userRepository.findByLogin(it) != null }
        val afterPermissions = mealPermissionRepository.findAllByStudentInAndDateBetween(
            demoStudents,
            seedStart,
            seedEnd,
        ).size
        val afterTransactions = mealTransactionRepository.findAllByTransactionHashStartingWith("demo_").size
        val afterMenu = countSeededMenuRows(seedStart, seedEnd)

        assertEquals(beforeUsers, afterUsers)
        assertEquals(beforePermissions, afterPermissions)
        assertEquals(beforeTransactions, afterTransactions)
        assertEquals(beforeMenu, afterMenu)
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
        assertTrue(mealTransactionRepository.findAllByTransactionHashStartingWith("demo_").isEmpty())
    }
}
