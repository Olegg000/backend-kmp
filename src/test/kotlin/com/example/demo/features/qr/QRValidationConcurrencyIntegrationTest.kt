package com.example.demo.features.qr

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.util.CryptoUtils
import com.example.demo.features.qr.dto.ValidateQRRequest
import com.example.demo.features.qr.service.QRCodeService
import com.example.demo.features.qr.service.QRValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("QR validation concurrency integration")
class QRValidationConcurrencyIntegrationTest {

    @Autowired
    private lateinit var validationService: QRValidationService

    @Autowired
    private lateinit var qrCodeService: QRCodeService

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

    private lateinit var student: UserEntity
    private lateinit var privateKey: String
    private lateinit var chef: UserEntity

    @BeforeEach
    fun setUp() {
        wipeAllDomainTables()

        val keys = CryptoUtils.generateKeyPair()
        val group = groupRepository.save(GroupEntity(groupName = "QR-CONC"))
        val curator = userRepository.save(
            UserEntity(
                login = "qr-curator",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Cur",
                surname = "Ator",
                fatherName = "X"
            )
        )
        chef = userRepository.save(
            UserEntity(
                login = "qr-chef",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Chef",
                surname = "Qr",
                fatherName = "X"
            )
        )
        student = userRepository.save(
            UserEntity(
                login = "qr-student",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Stu",
                surname = "Dent",
                fatherName = "X",
                group = group,
                publicKey = keys.first,
                encryptedPrivateKey = keys.second
            )
        )
        privateKey = keys.second
        permissionRepository.save(
            MealPermissionEntity(
                date = LocalDate.now(),
                student = student,
                assignedBy = curator,
                reason = "QR concurrency",
                isBreakfastAllowed = false,
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
    @DisplayName("Два одновременных validate одного QR: один успех, один ALREADY")
    fun `parallel validate same qr allows only one success`() {
        val ts = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(),
            ts,
            MealType.LUNCH,
            nonce,
            privateKey
        )
        val req = ValidateQRRequest(
            userId = student.id!!,
            timestamp = ts,
            mealType = MealType.LUNCH,
            nonce = nonce,
            signature = signature
        )

        val responses = runConcurrently(2) {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                chef.login,
                "n/a",
                listOf(SimpleGrantedAuthority("ROLE_CHEF"))
            )
            try {
                validationService.validateOnline(req)
            } finally {
                SecurityContextHolder.clearContext()
            }
        }

        val successCount = responses.count { it.isValid }
        val failedCodes = responses.filterNot { it.isValid }.mapNotNull { it.errorCode }
        assertEquals(1, successCount, "Only one parallel validation may succeed")
        assertEquals(1, failedCodes.size, "Exactly one call must be rejected")
        assertTrue(
            failedCodes.first() == "ALREADY_USED" || failedCodes.first() == "ALREADY_ATE",
            "Rejected call must return ALREADY_* business code"
        )

        val txHash = qrCodeService.generateTransactionHash(
            student.id.toString(), ts, MealType.LUNCH, nonce
        )
        val stored = transactionRepository.findAll().count { it.transactionHash == txHash }
        assertEquals(1, stored, "Exactly one DB transaction expected for same QR")
    }

    private fun <T> runConcurrently(workers: Int, action: () -> T): List<T> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val futures = (0 until workers).map {
                pool.submit(Callable {
                    start.await(5, TimeUnit.SECONDS)
                    action()
                })
            }
            start.countDown()
            return futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
