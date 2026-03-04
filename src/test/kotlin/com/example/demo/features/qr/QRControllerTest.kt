package com.example.demo.features.qr

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.core.util.CryptoUtils
import com.example.demo.features.qr.dto.OfflineTransactionDto
import com.example.demo.features.qr.dto.ValidateQRRequest
import com.example.demo.features.qr.service.QRCodeService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("QRController - REST API Tests")
class QRControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    @Autowired
    private lateinit var jwtUtils: JwtUtils

    @Autowired
    private lateinit var qrCodeService: QRCodeService

    private lateinit var chefToken: String
    private lateinit var studentToken: String
    private lateinit var student: UserEntity
    private lateinit var publicKey: String
    private lateinit var privateKey: String

    @BeforeEach
    fun setup() {
        // Генерируем ключи
        val keys = CryptoUtils.generateKeyPair()
        publicKey = keys.first
        privateKey = keys.second

        // Создаем студента
        student = userRepository.save(
            UserEntity(
                login = "test-student",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Тест",
                surname = "Студентов",
                fatherName = "Тестович",
                publicKey = publicKey,
                encryptedPrivateKey = privateKey
            )
        )

        // Создаем повара
        val chef = userRepository.save(
            UserEntity(
                login = "test-chef",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Тест",
                surname = "Поваров",
                fatherName = "Тестович"
            )
        )

        // Генерируем токены
        chefToken = jwtUtils.generateToken(chef.login, chef.roles)
        studentToken = jwtUtils.generateToken(student.login, student.roles)
    }

    @Test
    @DisplayName("POST /api/v1/qr/validate требует JWT токен")
    fun `validate endpoint requires authentication`() {
        // Given
        val validNonce = "n".repeat(16)
        val validSignature = "s".repeat(64)
        val request = ValidateQRRequest(
            UUID.randomUUID(), 0L, MealType.LUNCH, validNonce, validSignature
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/qr/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("POST /api/v1/qr/validate работает с валидным токеном повара")
    fun `validate endpoint works with chef token`() {
        // Given
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, privateKey
        )

        val request = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, signature
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/qr/validate")
                .header("Authorization", "Bearer $chefToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isValid").exists())
    }

    @Test
    @DisplayName("POST /api/v1/qr/validate-offline требует роль повара по умолчанию")
    fun `validate-offline endpoint requires chef token by default`() {
        // Given
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, privateKey
        )

        val request = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, signature
        )

        // When & Then - без Authorization header
        mockMvc.perform(
            post("/api/v1/qr/validate-offline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("POST /api/v1/qr/validate-offline со студенческим токеном -> 403")
    fun `validate-offline endpoint forbids student role`() {
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(), timestamp, MealType.LUNCH, nonce, privateKey
        )
        val request = ValidateQRRequest(
            student.id!!, timestamp, MealType.LUNCH, nonce, signature
        )

        mockMvc.perform(
            post("/api/v1/qr/validate-offline")
                .header("Authorization", "Bearer $studentToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
        )
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Студент НЕ может вызывать /validate (только повар)")
    fun `student cannot access validate endpoint`() {
        // Given
        val validNonce = "n".repeat(16)
        val validSignature = "s".repeat(64)
        val request = ValidateQRRequest(
            student.id!!, 0L, MealType.LUNCH, validNonce, validSignature
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/qr/validate")
                .header("Authorization", "Bearer $studentToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
        )
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("Возврат правильного errorCode при ошибке")
    fun `should return proper error code on validation failure`() {
        // Given - устаревший timestamp
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 120
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(), expiredTimestamp, MealType.LUNCH, nonce, privateKey
        )

        val request = ValidateQRRequest(
            student.id!!, expiredTimestamp, MealType.LUNCH, nonce, signature
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/qr/validate")
                .header("Authorization", "Bearer $chefToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isValid").value(false))
            .andExpect(jsonPath("$.errorCode").value("QR_EXPIRED"))
    }

    @Test
    @DisplayName("POST /api/v1/qr/sync отклоняет поддельную подпись")
    fun `sync endpoint rejects forged signature`() {
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val transaction = OfflineTransactionDto(
            userId = student.id.toString(),
            timestamp = timestamp,
            mealType = MealType.LUNCH,
            nonce = CryptoUtils.generateNonce(),
            signature = "A".repeat(64),
        )

        mockMvc.perform(
            post("/api/v1/qr/sync")
                .header("Authorization", "Bearer $chefToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listOf(transaction)))
                .with(csrf())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.successCount").value(0))
            .andExpect(jsonPath("$.failedCount").value(1))
            .andExpect(jsonPath("$.errors[0].reason").value("Неверная подпись"))
    }

    @Test
    @DisplayName("POST /api/v1/qr/sync принимает валидную подписанную транзакцию")
    fun `sync endpoint accepts valid signed transaction`() {
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val nonce = CryptoUtils.generateNonce()
        val signature = qrCodeService.generateSignature(
            student.id.toString(),
            timestamp,
            MealType.LUNCH,
            nonce,
            privateKey
        )

        val assigner = userRepository.save(
            UserEntity(
                login = "curator-sync",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Руководителевна",
            )
        )
        val mealDate = Instant.ofEpochSecond(timestamp).atZone(ZoneId.of("Europe/Samara")).toLocalDate()
        permissionRepository.save(
            MealPermissionEntity(
                date = mealDate,
                student = student,
                assignedBy = assigner,
                reason = "Тест sync",
                isBreakfastAllowed = false,
                isLunchAllowed = true,
            )
        )

        val transaction = OfflineTransactionDto(
            userId = student.id.toString(),
            timestamp = timestamp,
            mealType = MealType.LUNCH,
            nonce = nonce,
            signature = signature,
        )

        mockMvc.perform(
            post("/api/v1/qr/sync")
                .header("Authorization", "Bearer $chefToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listOf(transaction)))
                .with(csrf())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.failedCount").value(0))
    }
}
