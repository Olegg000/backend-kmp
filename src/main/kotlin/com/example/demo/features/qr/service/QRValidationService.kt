package com.example.demo.features.qr.service

import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.features.qr.dto.QRValidationError
import com.example.demo.features.qr.dto.ValidateQRRequest
import com.example.demo.features.qr.dto.ValidateQRResponse
import com.example.demo.features.qr.dto.OfflineTransactionDto
import com.example.demo.features.qr.dto.SyncResponse
import com.example.demo.features.qr.dto.SyncError
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
    @Transactional
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

        val chefLogin = SecurityContextHolder.getContext().authentication?.name
        val chef = chefLogin?.let { userRepository.findByLogin(it) }
            ?: return createErrorResponse(
                QRValidationError.INVALID_SIGNATURE,
                "${student.surname} ${student.name}",
                "Повар не найден"
            )

        val timestamp = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(req.timestamp),
            ZoneId.systemDefault()
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = txHash,
                timeStamp = timestamp,
                student = student,
                chef = chef,
                isOffline = false,
                mealType = req.mealType
            )
        )

        logger.info("Validation successful for ${student.login}")
        return ValidateQRResponse(
            isValid = true,
            studentName = "${student.surname} ${student.name}",
            groupName = student.group?.groupName
        )
    }


    /**
     * Синхронизация оффлайн-транзакций
     * Принимает список транзакций, накопленных поваром, и сохраняет их в БД
     */
    @Transactional
    fun syncOfflineTransactions(transactions: List<OfflineTransactionDto>): SyncResponse {
        logger.info("Syncing ${transactions.size} offline transactions")
        
        val chefLogin = SecurityContextHolder.getContext().authentication?.name
        val chef = chefLogin?.let { userRepository.findByLogin(it) }
            ?: throw IllegalStateException("Chef not found")

        var successCount = 0
        var failedCount = 0
        val errors = mutableListOf<SyncError>()

        transactions.forEach { tx ->
            try {
                // 1. Поиск студента
                val student = userRepository.findById(java.util.UUID.fromString(tx.userId)).getOrNull()
                if (student == null) {
                    failedCount++
                    errors.add(SyncError(tx.userId, "Student not found"))
                    return@forEach
                }

                // 2. Проверка подписи (игнорируем время, так как это пост-фактум синхронизация)
                val isSignatureValid = qrCodeService.verifySignature(
                    tx.userId,
                    tx.timestamp,
                    tx.mealType,
                    tx.nonce,
                    tx.signature,
                    student.publicKey ?: ""
                )

                if (!isSignatureValid) {
                    failedCount++
                    errors.add(SyncError(tx.userId, "Invalid signature"))
                    return@forEach
                }

                // 3. Проверка на дубликаты (хэш транзакции)
                val txHash = qrCodeService.generateTransactionHash(
                    tx.userId, tx.timestamp, tx.mealType, tx.nonce
                )
                
                if (transactionRepository.existsByTransactionHash(txHash)) {
                     // Уже синхронизировано - считаем как успех (идемпотентность), но не сохраняем дубль
                     successCount++
                     return@forEach
                }
                
                // 4. Сохранение
                 val timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(tx.timestamp),
                    ZoneId.systemDefault()
                )
                
                transactionRepository.save(
                    MealTransactionEntity(
                        transactionHash = txHash,
                        timeStamp = timestamp,
                        student = student,
                        chef = chef,
                        isOffline = true,
                        mealType = tx.mealType
                    )
                )
                successCount++

            } catch (e: Exception) {
                logger.error("Error syncing transaction for user ${tx.userId}", e)
                failedCount++
                errors.add(SyncError(tx.userId, "Internal error: ${e.message}"))
            }
        }

        return SyncResponse(successCount, failedCount, errors)
    }

    /**
     * Базовая проверка (работает без интернета)
     * 1. Проверка подписи (ECDSA)
     * 2. Проверка времени (±60 секунд) - ТОЛЬКО ДЛЯ ОНЛАЙН ВАЛИДАЦИИ
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
