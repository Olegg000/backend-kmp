package com.example.demo.features.report

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.reports.dto.AssignedByRole
import com.example.demo.features.reports.dto.AssignedByRoleFilter
import com.example.demo.features.reports.service.ReportsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@Import(ReportsService::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("ReportsService - Детальный отчет consumption")
class ReportsServiceTest(
    @Autowired private val reportsService: ReportsService,
    @Autowired private val transactionRepository: MealTransactionRepository,
    @Autowired private val permissionRepository: MealPermissionRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
) {

    private lateinit var admin: UserEntity
    private lateinit var curator: UserEntity
    private lateinit var chef: UserEntity
    private lateinit var group1: GroupEntity
    private lateinit var group2: GroupEntity
    private lateinit var studentG1: UserEntity
    private lateinit var studentG2: UserEntity

    @BeforeEach
    fun setup() {
        group1 = groupRepository.save(GroupEntity(groupName = "ИСП-21"))
        group2 = groupRepository.save(GroupEntity(groupName = "ИСП-22"))

        admin = userRepository.save(
            UserEntity(
                login = "admin-reports",
                passwordHash = "hash",
                roles = mutableSetOf(Role.ADMIN),
                name = "Админ",
                surname = "Системный",
                fatherName = "А"
            )
        )

        curator = userRepository.save(
            UserEntity(
                login = "curator-reports",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Б"
            )
        )

        group1.curators = mutableSetOf(curator)
        groupRepository.save(group1)

        chef = userRepository.save(
            UserEntity(
                login = "chef-reports",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Повар",
                surname = "Кухонный",
                fatherName = "В"
            )
        )

        studentG1 = userRepository.save(
            UserEntity(
                login = "student-g1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Первый",
                fatherName = "И",
                group = group1,
                studentCategory = StudentCategory.SVO
            )
        )

        studentG2 = userRepository.save(
            UserEntity(
                login = "student-g2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Петр",
                surname = "Второй",
                fatherName = "П",
                group = group2,
                studentCategory = StudentCategory.MANY_CHILDREN
            )
        )

        val today = LocalDate.now()
        permissionRepository.save(
            MealPermissionEntity(
                date = today,
                student = studentG1,
                assignedBy = curator,
                reason = "Кураторский табель",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
            )
        )
        permissionRepository.save(
            MealPermissionEntity(
                date = today,
                student = studentG2,
                assignedBy = admin,
                reason = "Админский табель",
                isBreakfastAllowed = true,
                isLunchAllowed = false,
            )
        )

        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "tx-g1-breakfast",
                timeStamp = LocalDateTime.now(),
                student = studentG1,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST,
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "tx-g2-breakfast",
                timeStamp = LocalDateTime.now(),
                student = studentG2,
                chef = chef,
                isOffline = true,
                mealType = MealType.BREAKFAST,
            )
        )
    }

    @Test
    @DisplayName("ADMIN видит строки всех групп")
    fun `admin can see all groups in consumption report`() {
        val today = LocalDate.now()

        val rows = reportsService.generateConsumptionReport(
            currentLogin = admin.login,
            startDate = today,
            endDate = today,
            groupId = null,
            assignedByRoleFilter = AssignedByRoleFilter.ALL
        )

        assertEquals(2, rows.size)
        assertTrue(rows.any { it.groupId == group1.id })
        assertTrue(rows.any { it.groupId == group2.id })
    }

    @Test
    @DisplayName("CURATOR видит только свои группы")
    fun `curator can see only assigned groups in consumption report`() {
        val today = LocalDate.now()

        val rows = reportsService.generateConsumptionReport(
            currentLogin = curator.login,
            startDate = today,
            endDate = today,
            groupId = null,
            assignedByRoleFilter = AssignedByRoleFilter.ALL
        )

        assertEquals(1, rows.size)
        assertEquals(group1.id, rows.first().groupId)
        assertEquals(studentG1.id, rows.first().studentId)
    }

    @Test
    @DisplayName("Фильтр assignedByRole=ADMIN возвращает только админские назначения")
    fun `assignedByRole filter should work`() {
        val today = LocalDate.now()

        val rows = reportsService.generateConsumptionReport(
            currentLogin = admin.login,
            startDate = today,
            endDate = today,
            groupId = null,
            assignedByRoleFilter = AssignedByRoleFilter.ADMIN
        )

        assertEquals(1, rows.size)
        assertEquals(AssignedByRole.ADMIN, rows.first().assignedByRole)
        assertEquals(studentG2.id, rows.first().studentId)
    }

    @Test
    @DisplayName("CSV экспорт содержит заголовок и обе строки")
    fun `exportToCsv should include header and data rows`() {
        val today = LocalDate.now()

        val csv = reportsService.exportToCsv(
            currentLogin = admin.login,
            startDate = today,
            endDate = today,
            groupId = null,
            assignedByRoleFilter = AssignedByRoleFilter.ALL
        )

        assertTrue(csv.startsWith("Дата,ID группы,Группа,Студент,Категория,Назначил,Завтрак использован,Обед использован"))
        assertTrue(csv.contains("ИСП-21"))
        assertTrue(csv.contains("ИСП-22"))
    }
}
