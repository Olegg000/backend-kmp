package com.example.demo.integration

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
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.roster.dto.UpdateRosterRequest
import com.example.demo.features.roster.service.RosterService
import com.example.demo.features.transactions.dto.TransactionSyncItem
import com.example.demo.features.transactions.service.TransactionsService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Полный сценарий работы системы:
 * 1. Куратор заполняет табель
 * 2. Студент генерирует QR
 * 3. Повар проверяет QR (оффлайн)
 * 4. Повар синхронизирует транзакции
 * 5. Проверка защиты от двойного прохода
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Full Flow Test - Полный цикл работы системы")
class FullFlowTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    @Autowired
    private lateinit var transactionRepository: MealTransactionRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var rosterService: RosterService

    @Autowired
    private lateinit var qrCodeService: QRCodeService

    @Autowired
    private lateinit var qrValidationService: QRValidationService

    @Autowired
    private lateinit var transactionsService: TransactionsService

    private lateinit var student: UserEntity
    private lateinit var curator: UserEntity
    private lateinit var chef: UserEntity
    private lateinit var group: GroupEntity
    private lateinit var publicKey: String
    private lateinit var privateKey: String

    @BeforeEach
    fun setup() {
        // === 0. Подготовка: Создаём ключи ===
        val keys = CryptoUtils.generateKeyPair()
        publicKey = keys.first
        privateKey = keys.second

        // === 1. Создание группы ===
        group = groupRepository.save(GroupEntity(groupName = "ПИ-21", curator = null))

        // === 2. Создание куратора ===
        curator = userRepository.save(
            UserEntity(
                login = "curator-test",
                passwordHash = passwordEncoder.encode("password"),
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Руководителевна",
                group = group
            )
        )
        group.curator = curator
        groupRepository.save(group)

        // === 3. Создание студента ===
        student = userRepository.save(
            UserEntity(
                login = "student-test",
                passwordHash = passwordEncoder.encode("password"),
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Петрович",
                group = group,
                publicKey = publicKey,
                encryptedPrivateKey = privateKey
            )
        )

        // === 4. Создание повара ===
        chef = userRepository.save(
            UserEntity(
                login = "chef-test",
                passwordHash = passwordEncoder.encode("password"),
                roles = mutableSetOf(Role.CHEF),
                name = "Мария",
                surname = "Поварова",
                fatherName = "Кулинаровна"
            )
        )
    }

    @Test
    @DisplayName("Сценарий 1: Успешный полный цикл (Happy Path)")
    fun `full happy path - from roster to meal transaction`() {
        val today = LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY)

        // === ШАГ 1: Куратор заполняет табель ===
        println("\n=== ШАГ 1: Куратор заполняет табель ===")
        val rosterRequest = UpdateRosterRequest(
            studentId = student.id!!,
            permissions = listOf(
                DayPermissionDto(today, true, true, false, false, false, "Учебный день")
            )
        )
        rosterService.updateRoster(rosterRequest, curator.login)

        // Проверка: табель сохранён
        val permission = permissionRepository.findByStudentAndDate(student, today)
        assertNotNull(permission, "Разрешение должно быть создано")
        assertTrue(permission!!.isBreakfastAllowed, "Завтрак разрешён")
        assertTrue(permission.isLunchAllowed, "Обед разрешён")

        println("✅ Табель заполнен: Завтрак=true, Обед=true")

        // === ШАГ 2: Студент генерирует QR-код ===
        println("\n=== ШАГ 2: Студент генерирует QR ===")
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val mealType = MealType.LUNCH

        val signature = qrCodeService.generateSignature(
            student.id.toString(),
            timestamp,
            mealType,
            nonce,
            privateKey
        )

        println("✅ QR сгенерирован (timestamp=$timestamp, nonce=${nonce.take(8)}...)")

        // === ШАГ 3: Повар сканирует QR (OFFLINE) ===
        println("\n=== ШАГ 3: Повар проверяет QR (оффлайн) ===")
        val qrRequest = ValidateQRRequest(
            userId = student.id!!,
            timestamp = timestamp,
            mealType = mealType,
            nonce = nonce,
            signature = signature
        )

        val offlineValidation = qrValidationService.validateOffline(qrRequest)
        assertTrue(offlineValidation.isValid, "QR должен быть валиден оффлайн")
        assertEquals("Студентов Иван", offlineValidation.studentName)

        println("✅ Оффлайн-проверка пройдена: ${offlineValidation.studentName}")

        // === ШАГ 4: Повар синхронизирует транзакцию ===
        println("\n=== ШАГ 4: Синхронизация транзакций ===")
        val txHash = qrCodeService.generateTransactionHash(
            student.id.toString(), timestamp, mealType, nonce
        )

        val syncItems = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = mealType,
                transactionHash = txHash
            )
        )

        val syncResponse = transactionsService.syncBatch(chef.login, syncItems)
        assertEquals(1, syncResponse.successCount, "Транзакция должна быть успешной")
        assertTrue(syncResponse.errors.isEmpty(), "Не должно быть ошибок")

        println("✅ Транзакция сохранена: successCount=${syncResponse.successCount}")

        // === ШАГ 5: Проверка защиты от двойного прохода ===
        println("\n=== ШАГ 5: Защита от Double Spending ===")
        val secondAttempt = transactionsService.syncBatch(chef.login, syncItems)
        assertEquals(1, secondAttempt.successCount, "Дубль должен быть проигнорирован (идемпотентность)")

        // Но новый QR с новым nonce должен быть отклонён (студент уже ел)
        val newNonce = CryptoUtils.generateNonce()
        val newSignature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, mealType, newNonce, privateKey
        )
        val newQrRequest = ValidateQRRequest(
            student.id!!, timestamp, mealType, newNonce, newSignature
        )

        val onlineValidation = qrValidationService.validateOnline(newQrRequest)
        assertFalse(onlineValidation.isValid, "Второй проход должен быть отклонён")
        assertEquals("ALREADY_ATE", onlineValidation.errorCode)

        println("✅ Double Spending заблокирован: errorCode=${onlineValidation.errorCode}")

        // === ИТОГ ===
        println("\n=== ИТОГ: Полный цикл выполнен успешно! ===")
        val savedTx = transactionRepository.findAll()
        assertEquals(1, savedTx.size, "В базе должна быть ровно 1 транзакция")
    }

    @Test
    @DisplayName("Сценарий 2: Отклонение без разрешения в табеле")
    fun `student cannot pass without permission in roster`() {
        // НЕ заполняем табель!

        // Студент генерирует QR
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, privateKey
        )

        val qrRequest = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, signature
        )

        // Оффлайн-проверка пройдёт (крипто ОК)
        val offlineCheck = qrValidationService.validateOffline(qrRequest)
        assertTrue(offlineCheck.isValid)

        // Но онлайн-проверка отклонит (нет в табеле)
        val onlineCheck = qrValidationService.validateOnline(qrRequest)
        assertFalse(onlineCheck.isValid)
        assertEquals("NO_PERMISSION", onlineCheck.errorCode)

        println("✅ Система корректно блокирует студентов без разрешения")
    }

    @Test
    @DisplayName("Сценарий 3: Отклонение поддельного QR")
    fun `forged QR code should be rejected`() {
        // Заполняем табель
        val today = LocalDate.now()
        permissionRepository.save(
            MealPermissionEntity(
                date = today,
                student = student,
                assignedBy = curator,
                reason = "Тест",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
                isDinnerAllowed = false,
                isSnackAllowed = false,
                isSpecialAllowed = false
            )
        )

        // Генерируем QR с ЧУЖИМ ключом (атака!)
        val fakeKeys = CryptoUtils.generateKeyPair()
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val fakeSignature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, fakeKeys.second
        )

        val fakeQr = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, fakeSignature
        )

        // Проверка должна провалиться
        val validation = qrValidationService.validateOffline(fakeQr)
        assertFalse(validation.isValid)
        assertEquals("INVALID_SIG", validation.errorCode)

        println("✅ Подделка обнаружена: INVALID_SIG")
    }

    @Test
    @DisplayName("Сценарий 4: Устаревший QR отклоняется")
    fun `expired QR code should be rejected`() {
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 120 // 2 минуты назад
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(), expiredTimestamp, MealType.LUNCH, nonce, privateKey
        )

        val expiredQr = ValidateQRRequest(
            student.id!!, expiredTimestamp, MealType.LUNCH, nonce, signature
        )

        val validation = qrValidationService.validateOffline(expiredQr)
        assertFalse(validation.isValid)
        assertEquals("QR_EXPIRED", validation.errorCode)

        println("✅ Устаревший QR отклонён: QR_EXPIRED")
    }
}