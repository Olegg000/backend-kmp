package com.example.demo.features.report

import com.example.demo.config.TestProfileResolver
import com.example.demo.config.TimeConfig
import com.example.demo.core.database.CuratorWeekFillStatus
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.CuratorWeekAuditEntity
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.CuratorWeekAuditRepository
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.reports.dto.AssignedByRole
import com.example.demo.features.reports.dto.AssignedByRoleFilter
import com.example.demo.features.reports.service.ReportsService
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@Import(ReportsService::class, RosterWeekPolicy::class, TimeConfig::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("ReportsService - Детальный отчет consumption")
class ReportsServiceTest(
    @Autowired private val reportsService: ReportsService,
    @Autowired private val transactionRepository: MealTransactionRepository,
    @Autowired private val permissionRepository: MealPermissionRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val curatorWeekAuditRepository: CuratorWeekAuditRepository,
) {

    private lateinit var admin: UserEntity
    private lateinit var curator: UserEntity
    private lateinit var chef: UserEntity
    private lateinit var group1: GroupEntity
    private lateinit var group2: GroupEntity
    private lateinit var studentG1: UserEntity
    private lateinit var studentG1NoPermission: UserEntity
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

        studentG1NoPermission = userRepository.save(
            UserEntity(
                login = "student-g1-no-perm",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Анна",
                surname = "Третья",
                fatherName = "А",
                group = group1,
                studentCategory = StudentCategory.SVO
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

        assertEquals(3, rows.size)
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

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.groupId == group1.id })
        assertTrue(rows.any { it.studentId == studentG1.id })
        assertTrue(rows.any { it.studentId == studentG1NoPermission.id })
    }

    @Test
    @DisplayName("CURATOR не может запросить чужую группу по groupId")
    fun `curator cannot request foreign group`() {
        val today = LocalDate.now()
        val ex = assertThrows(BusinessException::class.java) {
            reportsService.generateConsumptionReport(
                currentLogin = curator.login,
                startDate = today,
                endDate = today,
                groupId = group2.id,
                assignedByRoleFilter = AssignedByRoleFilter.ALL
            )
        }

        assertEquals("CURATOR_GROUP_ACCESS_DENIED", ex.code)
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
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
    @DisplayName("Отчет включает студента без табеля и показывает данные сканирования")
    fun `report should include students without permission and scanner info`() {
        val today = LocalDate.now()

        val rows = reportsService.generateConsumptionReport(
            currentLogin = admin.login,
            startDate = today,
            endDate = today,
            groupId = null,
            assignedByRoleFilter = AssignedByRoleFilter.ALL
        )

        val noPermissionRow = rows.first { it.studentId == studentG1NoPermission.id }
        assertEquals(false, noPermissionRow.breakfastUsed)
        assertEquals(false, noPermissionRow.lunchUsed)
        assertEquals(null, noPermissionRow.assignedByRole)
        assertEquals(null, noPermissionRow.assignedByName)
        assertEquals(null, noPermissionRow.breakfastTransactionId)
        assertEquals(null, noPermissionRow.lunchTransactionId)

        val g1Row = rows.first { it.studentId == studentG1.id }
        assertEquals(AssignedByRole.CURATOR, g1Row.assignedByRole)
        assertEquals("Классова Мария Б", g1Row.assignedByName)
        assertTrue(g1Row.breakfastUsed)
        assertTrue(g1Row.breakfastTransactionId != null)
        assertEquals("Кухонный Повар В", g1Row.breakfastScannedByName)
    }

    @Test
    @DisplayName("CSV экспорт содержит расширенный заголовок и строки")
    fun `exportToCsv should include header and data rows`() {
        val today = LocalDate.now()

        val csv = reportsService.exportToCsv(
            currentLogin = admin.login,
            startDate = today,
            endDate = today,
            groupId = null,
            assignedByRoleFilter = AssignedByRoleFilter.ALL
        )

        assertTrue(csv.startsWith("Дата,ID группы,Группа,ID студента,Студент,Категория,Роль назначившего,ФИО назначившего"))
        assertTrue(csv.contains("ID транзакции завтрака"))
        assertTrue(csv.contains("ФИО сканировавшего обед"))
        assertTrue(csv.contains("ИСП-21"))
        assertTrue(csv.contains("ИСП-22"))
    }

    @Test
    @DisplayName("Summary содержит агрегаты 3 единиц и zero-fill кураторов")
    fun `summary should include aggregates and zero fill curators`() {
        val today = LocalDate.now()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        curatorWeekAuditRepository.save(
            CuratorWeekAuditEntity(
                curator = curator,
                weekStart = weekStart,
                filledCells = 0,
                expectedCells = 10,
                fillStatus = CuratorWeekFillStatus.ZERO_FILL,
                lockedAt = LocalDateTime.now(),
            )
        )

        val summary = reportsService.generateConsumptionSummary(
            currentLogin = admin.login,
            startDate = today,
            endDate = today,
            groupId = null,
            assignedByRoleFilter = AssignedByRoleFilter.ALL
        )

        assertTrue(summary.totalBreakfastCount >= 2)
        assertTrue(summary.totalLunchCount >= 1)
        assertTrue(summary.zeroFillCurators.any { it.curatorId == curator.id })
    }
}
