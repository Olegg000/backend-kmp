package com.example.demo.features.qr

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.util.CryptoUtils
import com.example.demo.features.qr.dto.ValidateQRRequest
import com.example.demo.features.qr.service.QRCodeService
import com.example.demo.features.qr.service.QRValidationService
import org.junit.jupiter.api.Assertions.*
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
@Import(QRValidationService::class, QRCodeService::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("QRValidation - Integration Tests (полный цикл)")
class QRValidationIntegrationTest {

    @Autowired
    private lateinit var qrValidationService: QRValidationService

    @Autowired
    private lateinit var qrCodeService: QRCodeService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    @Autowired
    private lateinit var transactionRepository: MealTransactionRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    private lateinit var student: UserEntity
    private lateinit var curator: UserEntity
    private lateinit var publicKey: String
    private lateinit var privateKey: String

    @BeforeEach
    fun setup() {
        // Генерируем ключи
        val keys = CryptoUtils.generateKeyPair()
        publicKey = keys.first
        privateKey = keys.second

        // Создаем группу
        val group = groupRepository.save(GroupEntity(groupName = "Test Group", curator = null))

        // Создаем куратора
        curator = userRepository.save(
            UserEntity(
                login = "curator",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Куратор",
                surname = "Тестовый",
                fatherName = "Кураторович"
            )
        )

        // Создаем студента с ключами
        student = userRepository.save(
            UserEntity(
                login = "student",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент",
                surname = "Тестовый",
                fatherName = "Студентович",
                group = group,
                publicKey = publicKey,
                encryptedPrivateKey = privateKey
            )
        )

        // Даем разрешение на сегодня
        permissionRepository.save(
            MealPermissionEntity(
                date = LocalDate.now(),
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
    }

    @Test
    @DisplayName("Полный цикл: генерация QR → валидация → успех")
    fun `full flow - generate QR and validate successfully`() {
        // Given - студент генерирует QR
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val mealType = MealType.LUNCH
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, mealType, nonce, privateKey
        )

        val request = ValidateQRRequest(
            userId = student.id!!,
            timestamp = timestamp,
            mealType = mealType,
            nonce = nonce,
            signature = signature
        )

        // When - повар проверяет
        val response = qrValidationService.validateOnline(request)

        // Then
        assertTrue(response.isValid, "QR должен быть валидным")
        assertEquals("Тестовый Студент", response.studentName)
        assertNull(response.errorCode)
    }

    @Test
    @DisplayName("Отклонение устаревшего QR (старше 60 секунд)")
    fun `should reject expired QR code`() {
        // Given - QR с timestamp 2 минуты назад
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 120
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(
            student.id.toString(), expiredTimestamp, MealType.LUNCH, nonce, privateKey
        )

        val request = ValidateQRRequest(
            student.id!!, expiredTimestamp, MealType.LUNCH, nonce, signature
        )

        // When
        val response = qrValidationService.validateOnline(request)

        // Then
        assertFalse(response.isValid)
        assertEquals("QR_EXPIRED", response.errorCode)
    }

    @Test
    @DisplayName("Отклонение поддельного QR (неверная подпись)")
    fun `should reject forged QR with invalid signature`() {
        // Given - валидные данные, но подпись с другого ключа
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()

        val fakeKeys = CryptoUtils.generateKeyPair()
        val fakeSignature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, fakeKeys.second
        )

        val request = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, fakeSignature
        )

        // When
        val response = qrValidationService.validateOnline(request)

        // Then
        assertFalse(response.isValid)
        assertEquals("INVALID_SIG", response.errorCode)
    }

    @Test
    @DisplayName("Отклонение при отсутствии разрешения в табеле")
    fun `should reject when no permission in roster`() {
        // Given - студент пытается поесть ужин (нет разрешения)
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.DINNER, nonce, privateKey
        )

        val request = ValidateQRRequest(
            student.id!!, timestamp, MealType.DINNER, nonce, signature
        )

        // When
        val response = qrValidationService.validateOnline(request)

        // Then
        assertFalse(response.isValid)
        assertEquals("NO_PERMISSION", response.errorCode)
    }

    @Test
    @DisplayName("Double Spending: отклонение повторного использования QR")
    fun `should prevent double spending`() {
        // Given - первый проход
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, privateKey
        )

        val request = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, signature
        )

        // First validation - success
        val response1 = qrValidationService.validateOnline(request)
        assertTrue(response1.isValid)

        // Сохраняем транзакцию вручную (как это сделает повар)
        val chef = userRepository.save(
            UserEntity(
                login = "chef",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Повар",
                surname = "Кулинаров",
                fatherName = "Поварович"
            )
        )

        val txHash = qrCodeService.generateTransactionHash(
            student.id.toString(), timestamp, MealType.LUNCH, nonce
        )

        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = txHash,
                timeStamp = LocalDateTime.now(),
                student = student,
                chef = chef,
                isOffline = false,
                mealType = MealType.LUNCH
            )
        )

        // When - попытка второго прохода (даже с новым QR!)
        val newNonce = CryptoUtils.generateNonce()
        val newSignature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, newNonce, privateKey
        )
        val request2 = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, newNonce, newSignature
        )

        val response2 = qrValidationService.validateOnline(request2)

        // Then
        assertFalse(response2.isValid, "Второй проход должен быть отклонен")
        assertEquals("ALREADY_ATE", response2.errorCode)
    }

    @Test
    @DisplayName("Оффлайн валидация работает без БД")
    fun `offline validation should work without database checks`() {
        // Given
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, privateKey
        )

        val request = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, signature
        )

        // When - оффлайн валидация (не проверяет табель и double spending)
        val response = qrValidationService.validateOffline(request)

        // Then
        assertTrue(response.isValid, "Оффлайн валидация должна пройти (только крипто + время)")
    }

    @Test
    @DisplayName("Защита от подмены данных в QR")
    fun `should detect data tampering`() {
        // Given - генерируем валидный QR для завтрака
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.BREAKFAST, nonce, privateKey
        )

        // When - пытаемся проверить с другим типом еды (подделка!)
        val tamperedRequest = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, signature // LUNCH вместо BREAKFAST
        )

        val response = qrValidationService.validateOnline(tamperedRequest)

        // Then
        assertFalse(response.isValid, "Подделка должна быть обнаружена")
        assertEquals("INVALID_SIG", response.errorCode)
    }
}