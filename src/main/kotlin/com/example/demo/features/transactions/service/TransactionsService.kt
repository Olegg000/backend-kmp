package com.example.demo.features.transactions.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.exception.BusinessException
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.SuspiciousTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.SuspiciousTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.transactions.dto.SyncResponse
import com.example.demo.features.transactions.dto.TransactionSyncProcessedItem
import com.example.demo.features.transactions.dto.TransactionSyncItem
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class TransactionsService(
    private val transactionRepository: MealTransactionRepository,
    private val permissionRepository: MealPermissionRepository,
    private val userRepository: UserRepository,
    private val suspiciousTransactionRepository: SuspiciousTransactionRepository,
    private val businessZoneId: ZoneId,
) {
    // Принимаем логин повара (кто отправил) и список транзакций
    @Transactional
    fun syncBatch(chefLogin: String, items: List<TransactionSyncItem>): SyncResponse {
        val chef = userRepository.findByLogin(chefLogin)
            ?: throw BusinessException(
                code = "CHEF_NOT_FOUND",
                userMessage = "Повар не найден",
            )

        var success = 0
        val errorMessages = mutableListOf<String>()
        val processed = mutableListOf<TransactionSyncProcessedItem>()

        for (item in items) {
            try {
                val outcome = processSingleTransaction(item, chef)
                success++
                processed += TransactionSyncProcessedItem(
                    transactionHash = item.transactionHash,
                    studentId = item.studentId,
                    status = if (outcome == TransactionProcessOutcome.IDEMPOTENT) "IDEMPOTENT" else "SUCCESS",
                )
            } catch (e: Exception) {
                // Логируем ошибку, но не прерываем весь цикл.
                // Остальные студенты должны быть обработаны.
                val safeMessage = when (e) {
                    is BusinessException -> e.userMessage
                    else -> "Не удалось обработать транзакцию"
                }
                errorMessages.add("Student ${item.studentId}: $safeMessage")
                processed += TransactionSyncProcessedItem(
                    transactionHash = item.transactionHash,
                    studentId = item.studentId,
                    status = "FAILED",
                    code = (e as? BusinessException)?.code ?: "INTERNAL_ERROR",
                    message = safeMessage,
                )
            }
        }

        return SyncResponse(success, errorMessages, processed = processed)
    }

    private fun processSingleTransaction(item: TransactionSyncItem, chef: UserEntity): TransactionProcessOutcome {
        // 1. Проверка дублей по хэшу (защита от повторной отправки пакета)
        if (item.transactionHash != null && transactionRepository.existsByTransactionHash(item.transactionHash)) {
            return TransactionProcessOutcome.IDEMPOTENT
        }

        val txTimestamp = resolveTimestamp(item)

        // 2. Ищем студента
        val student = userRepository.findByIdForUpdate(item.studentId)
            ?: throw BusinessException(
                code = "STUDENT_NOT_FOUND",
                userMessage = "Студент не найден",
            )

        // Повторная проверка хэша после блокировки студента:
        // закрывает гонку, когда параллельный поток уже успел закоммитить ту же транзакцию.
        if (item.transactionHash != null && transactionRepository.existsByTransactionHash(item.transactionHash)) {
            return TransactionProcessOutcome.IDEMPOTENT
        }

        // 3. Работа с датой (игнорируем время транзакции для проверки лимитов, смотрим только на день)
        val dateOfMeal = txTimestamp.toLocalDate()
        val startOfDay = dateOfMeal.atStartOfDay()
        val endOfDay = dateOfMeal.atTime(LocalTime.MAX)

        // 4. Double Spending: Ел ли уже этот тип еды сегодня?
        val alreadyAte = transactionRepository.existsByStudentAndMealTypeAndTimeStampBetween(
            student, item.mealType, startOfDay, endOfDay
        )
        if (alreadyAte) {
            val existing = transactionRepository.findAllByStudentAndTimeStampBetween(
                student, startOfDay, endOfDay
            ).firstOrNull { it.mealType == item.mealType }

            suspiciousTransactionRepository.save(
                SuspiciousTransactionEntity(
                    student = student,
                    chef = chef,
                    date = dateOfMeal,
                    mealType = item.mealType,
                    reason = "ALREADY_ATE",
                    baseTransactionHash = existing?.transactionHash,
                    attemptTransactionHash = item.transactionHash
                )
            )

            throw BusinessException(
                code = "ALREADY_ATE",
                userMessage = "Студент уже получил ${item.mealType} за эту дату",
            )
        }

        if (student.studentCategory == StudentCategory.MANY_CHILDREN) {
            val hasAnyMealToday = transactionRepository.findAllByStudentAndTimeStampBetween(
                student, startOfDay, endOfDay
            ).isNotEmpty()
            if (hasAnyMealToday) {
                throw BusinessException(
                    code = "MANY_CHILDREN_LIMIT",
                    userMessage = "Категория 'Многодетные' допускает только один прием пищи в день",
                )
            }
        }

        // 5. Permission Check: Есть ли галочка в табеле?
        val permission = permissionRepository.findByStudentAndDate(student, dateOfMeal)

        val isAllowed = when (item.mealType) {
            MealType.BREAKFAST -> permission?.isBreakfastAllowed == true
            MealType.LUNCH -> permission?.isLunchAllowed == true
        }

        if (!isAllowed) {
            // Если повар накормил без разрешения (оффлайн ошибка) - мы отклоняем транзакцию.
            // В будущем тут можно сохранять в таблицу FraudLog.
            throw BusinessException(
                code = "NO_PERMISSION",
                userMessage = "Нет разрешения в табеле на ${item.mealType}",
            )
        }

        // 6. Сохранение
        val entity = MealTransactionEntity(
            // Если хэша нет (старая версия приложения), генерируем случайный
            transactionHash = item.transactionHash ?: UUID.randomUUID().toString(),
            timeStamp = txTimestamp,
            student = student,
            chef = chef,
            mealType = item.mealType,
            isOffline = true // Пакетная загрузка всегда считается оффлайн-синхронизацией
        )
        try {
            transactionRepository.save(entity)
        } catch (e: DataIntegrityViolationException) {
            // Идемпотентность по transaction_hash: если запись уже есть, считаем успешным повтор.
            if (item.transactionHash != null && transactionRepository.existsByTransactionHash(item.transactionHash)) {
                return TransactionProcessOutcome.IDEMPOTENT
            }
            throw e
        }
        return TransactionProcessOutcome.STORED
    }

    private fun resolveTimestamp(item: TransactionSyncItem): LocalDateTime {
        item.timestampEpochSec?.let { epoch ->
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), businessZoneId)
        }
        item.timestamp?.let { legacy ->
            return legacy
        }
        throw BusinessException(
            code = "TIMESTAMP_REQUIRED",
            userMessage = "Отсутствует время транзакции",
        )
    }

    private enum class TransactionProcessOutcome {
        STORED,
        IDEMPOTENT,
    }
}
