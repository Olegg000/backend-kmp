package com.example.demo.features.qr.service

import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.qr.dto.QRValidationError
import com.example.demo.features.qr.dto.ValidateQRRequest
import com.example.demo.features.qr.dto.ValidateQRResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.jvm.optionals.getOrNull

@Service
class QRValidationService(
    private val qrCodeService: QRCodeService,
    private val userRepository: UserRepository,
    private val permissionRepository: MealPermissionRepository,
    private val transactionRepository: MealTransactionRepository
) {
    private val logger = LoggerFactory.getLogger(QRValidationService::class.java)

    /**
     * Онлайн-валидация (с проверкой БД)
     * Полная проверка всех условий перед выдачей еды
     */
    fun validateOnline(req: ValidateQRRequest): ValidateQRResponse {
        logger.info("Online validation started for user: ${req.userId}")

        // 1. Базовая проверка (криптография + время)
        val basicCheck = performBasicValidation(req)
        if (!basicCheck.isValid) {
            logger.warn("Basic validation failed: ${basicCheck.errorMessage}")
            return basicCheck
        }

        val student = userRepository.findById(req.userId).getOrNull()
            ?: return createErrorResponse(QRValidationError.STUDENT_NOT_FOUND)

        // 2. Проверка Double Spending (БД) - главная защита
        val date = LocalDate.now()
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.atTime(LocalTime.MAX)

        val alreadyAte = transactionRepository.existsByStudentAndMealTypeAndTimeStampBetween(
            student, req.mealType, startOfDay, endOfDay
        )
        if (alreadyAte) {
            logger.warn("Student ${student.login} already ate ${req.mealType} today")
            return createErrorResponse(
                QRValidationError.ALREADY_ATE,
                "${student.surname} ${student.name}"
            )
        }

        // 3. Проверка хеша транзакции (защита от повторной отправки одного и того же QR)
        val txHash = qrCodeService.generateTransactionHash(
            req.userId.toString(), req.timestamp, req.mealType, req.nonce
        )
        if (transactionRepository.existsByTransactionHash(txHash)) {
            logger.warn("Transaction hash already exists: $txHash")
            return createErrorResponse(
                QRValidationError.ALREADY_USED,
                "${student.surname} ${student.name}"
            )
        }

        // 4. Проверка разрешения в табеле
        val permission = permissionRepository.findByStudentAndDate(student, date)
        val isAllowed = when (req.mealType) {
            com.example.demo.core.database.MealType.BREAKFAST -> permission?.isBreakfastAllowed == true
            com.example.demo.core.database.MealType.LUNCH -> permission?.isLunchAllowed == true
            com.example.demo.core.database.MealType.DINNER -> permission?.isDinnerAllowed == true
            com.example.demo.core.database.MealType.SNACK -> permission?.isSnackAllowed == true
            com.example.demo.core.database.MealType.SPECIAL -> permission?.isSpecialAllowed == true
            else -> false
        }

        if (!isAllowed) {
            logger.warn("No permission for ${student.login} on ${req.mealType}")
            return createErrorResponse(
                QRValidationError.NO_PERMISSION,
                "${student.surname} ${student.name}"
            )
        }

        logger.info("Validation successful for ${student.login}")
        return ValidateQRResponse(
            isValid = true,
            studentName = "${student.surname} ${student.name}",
            groupName = student.group?.groupName
        )
    }

    /**
     * Оффлайн-валидация (без БД, только криптография + время)
     * Используется поваром, когда нет интернета
     *
     * ВАЖНО: Это не даёт 100% защиту от double spending между разными поварами,
     * но защищает от подделки QR и повторного использования у одного повара
     */
    fun validateOffline(req: ValidateQRRequest): ValidateQRResponse {
        logger.info("Offline validation started for user: ${req.userId}")
        return performBasicValidation(req)
    }

    /**
     * Базовая проверка (работает без интернета)
     * 1. Проверка подписи (ECDSA)
     * 2. Проверка времени (±60 секунд)
     */
    private fun performBasicValidation(req: ValidateQRRequest): ValidateQRResponse {
        // 1. Проверка времени (±60 секунд от текущего момента)
        if (!qrCodeService.isTimestampValid(req.timestamp)) {
            val now = System.currentTimeMillis() / 1000
            val diff = kotlin.math.abs(now - req.timestamp)
            logger.warn("Timestamp validation failed. Diff: ${diff}s")
            return createErrorResponse(QRValidationError.EXPIRED)
        }

        // 2. Поиск пользователя и его публичного ключа
        val user = userRepository.findById(req.userId).getOrNull()
            ?: return createErrorResponse(QRValidationError.STUDENT_NOT_FOUND)

        val publicKey = user.publicKey
            ?: return createErrorResponse(
                QRValidationError.INVALID_SIGNATURE,
                "${user.surname} ${user.name}",
                "У пользователя нет публичного ключа"
            )

        // 3. Проверка подписи (основная защита от подделки)
        val isSignatureValid = qrCodeService.verifySignature(
            req.userId.toString(),
            req.timestamp,
            req.mealType,
            req.nonce,
            req.signature,
            publicKey
        )

        if (!isSignatureValid) {
            logger.error("Invalid signature for user ${user.login}")
            return createErrorResponse(
                QRValidationError.INVALID_SIGNATURE,
                "${user.surname} ${user.name}"
            )
        }

        return ValidateQRResponse(
            isValid = true,
            studentName = "${user.surname} ${user.name}",
            groupName = user.group?.groupName
        )
    }

    /**
     * Вспомогательная функция для создания ошибок
     */
    private fun createErrorResponse(
        error: QRValidationError,
        studentName: String? = null,
        customMessage: String? = null
    ): ValidateQRResponse {
        return ValidateQRResponse(
            isValid = false,
            studentName = studentName,
            errorMessage = customMessage ?: error.message,
            errorCode = error.code
        )
    }
}