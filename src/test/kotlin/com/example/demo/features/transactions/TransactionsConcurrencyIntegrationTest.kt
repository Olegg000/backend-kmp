package com.example.demo.features.transactions

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.transactions.dto.TransactionSyncItem
import com.example.demo.features.transactions.service.TransactionsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("Transactions concurrency integration")
class TransactionsConcurrencyIntegrationTest {

    @Autowired
    private lateinit var transactionsService: TransactionsService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    @Autowired
    private lateinit var transactionRepository: MealTransactionRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var chef: UserEntity
    private lateinit var student: UserEntity

    @BeforeEach
    fun setUp() {
        wipeAllDomainTables()

        val group = groupRepository.save(GroupEntity(groupName = "T-CONC"))
        val curator = userRepository.save(
            UserEntity(
                login = "curator-conc",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Cur",
                surname = "Ator",
                fatherName = "X",
                group = group
            )
        )
        chef = userRepository.save(
            UserEntity(
                login = "chef-conc",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Chef",
                surname = "Test",
                fatherName = "X"
            )
        )
        student = userRepository.save(
            UserEntity(
                login = "student-conc",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Stu",
                surname = "Dent",
                fatherName = "X",
                group = group
            )
        )
        permissionRepository.save(
            MealPermissionEntity(
                date = LocalDate.now(),
                student = student,
                assignedBy = curator,
                reason = "Concurrency test",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
            )
        )
    }

    private fun wipeAllDomainTables() {
        jdbcTemplate.update("DELETE FROM meal_transaction")
        jdbcTemplate.update("DELETE FROM suspicious_transaction")
        jdbcTemplate.update("DELETE FROM meal_permission")
        jdbcTemplate.update("DELETE FROM notifications")
        jdbcTemplate.update("DELETE FROM password_reset_log")
        jdbcTemplate.update("DELETE FROM group_curators")
        jdbcTemplate.update("DELETE FROM user_roles")
        jdbcTemplate.update("DELETE FROM users")
        jdbcTemplate.update("DELETE FROM groups")
    }

    @Test
    @DisplayName("Параллельный sync одного transactionHash сохраняет ровно одну транзакцию")
    fun `parallel same hash stays idempotent`() {
        val ts = LocalDate.now().atTime(12, 0)
        val item = TransactionSyncItem(
            studentId = student.id!!,
            timestamp = ts,
            mealType = MealType.LUNCH,
            transactionHash = "parallel-same-hash"
        )

        val responses = runConcurrently(2) {
            transactionsService.syncBatch(chef.login, listOf(item))
        }

        assertEquals(2, responses.size)
        assertTrue(responses.all { it.successCount == 1 }, "Both concurrent calls must be idempotent-success")
        val stored = transactionRepository.findAll().count { it.transactionHash == "parallel-same-hash" }
        assertEquals(1, stored, "Only one DB row must exist for shared hash")
    }

    @Test
    @DisplayName("Many-children гонка: два разных приема пищи одновременно -> не более одного успеха")
    fun `many children race allows only one meal`() {
        student.studentCategory = StudentCategory.MANY_CHILDREN
        userRepository.save(student)

        val baseTs = LocalDate.now().atTime(12, 0)
        val breakfast = TransactionSyncItem(
            studentId = student.id!!,
            timestamp = baseTs,
            mealType = MealType.BREAKFAST,
            transactionHash = "parallel-many-children-bf"
        )
        val lunch = TransactionSyncItem(
            studentId = student.id!!,
            timestamp = baseTs.plusHours(1),
            mealType = MealType.LUNCH,
            transactionHash = "parallel-many-children-ln"
        )

        val responses = runConcurrently(2) { index ->
            val item = if (index == 0) breakfast else lunch
            transactionsService.syncBatch(chef.login, listOf(item))
        }

        val successTotal = responses.sumOf { it.successCount }
        assertEquals(1, successTotal, "Race must not allow two successful meals for MANY_CHILDREN")

        val dayStart = LocalDate.now().atStartOfDay()
        val dayEnd = LocalDate.now().atTime(23, 59, 59)
        val persisted = transactionRepository.findAllByStudentAndTimeStampBetween(student, dayStart, dayEnd).size
        assertEquals(1, persisted, "Exactly one persisted meal expected")
    }

    private fun <T> runConcurrently(workers: Int, action: (Int) -> T): List<T> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val futures = (0 until workers).map { idx ->
                pool.submit(Callable {
                    start.await(5, TimeUnit.SECONDS)
                    action(idx)
                })
            }
            start.countDown()
            return futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
