package com.example.demo.features.transactions.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.SuspiciousTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.SuspiciousTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.transactions.dto.SyncResponse
import com.example.demo.features.transactions.dto.TransactionSyncItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class TransactionsService(
    private val transactionRepository: MealTransactionRepository,
    private val permissionRepository: MealPermissionRepository,
    private val userRepository: UserRepository,
    private val suspiciousTransactionRepository: SuspiciousTransactionRepository
) {

    // Принимаем логин повара (кто отправил) и список транзакций
    @Transactional
    fun syncBatch(chefLogin: String, items: List<TransactionSyncItem>): SyncResponse {
        val chef = userRepository.findByLogin(chefLogin)
            ?: throw RuntimeException("Повар не найден")

        var success = 0
        val errorMessages = mutableListOf<String>()

        for (item in items) {
            try {
                processSingleTransaction(item, chef)
                success++
            } catch (e: Exception) {
                // Логируем ошибку, но не прерываем весь цикл.
                // Остальные студенты должны быть обработаны.
                errorMessages.add("Student ${item.studentId}: ${e.message}")
            }
        }

        return SyncResponse(success, errorMessages)
    }

    private fun processSingleTransaction(item: TransactionSyncItem, chef: UserEntity) {
        // 1. Проверка дублей по хэшу (защита от повторной отправки пакета)
        if (item.transactionHash != null && transactionRepository.existsByTransactionHash(item.transactionHash)) {
            return // Уже сохранено, просто пропускаем
        }

        // 2. Ищем студента
        val student = userRepository.findById(item.studentId).getOrNull()
            ?: throw RuntimeException("Студент не найден (ID: ${item.studentId})")

        // 3. Работа с датой (игнорируем время транзакции для проверки лимитов, смотрим только на день)
        val dateOfMeal = item.timestamp.toLocalDate()
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

            throw RuntimeException("Студент уже получил ${item.mealType} за эту дату!")
        }

        // 5. Permission Check: Есть ли галочка в табеле?
        val permission = permissionRepository.findByStudentAndDate(student, dateOfMeal)

        // ВОТ ТУТ ВАЖНОЕ ОБНОВЛЕНИЕ ДЛЯ ПОЛДНИКА И СПЕЦ. ПИТАНИЯ
        val isAllowed = when (item.mealType) {
            MealType.BREAKFAST -> permission?.isBreakfastAllowed == true
            MealType.LUNCH -> permission?.isLunchAllowed == true
            MealType.DINNER -> permission?.isDinnerAllowed == true
            MealType.SNACK -> permission?.isSnackAllowed == true     // Добавил
            MealType.SPECIAL -> permission?.isSpecialAllowed == true // Добавил
            // Если добавишь что-то новое в Enum, но забудешь тут - по дефолту запрещено
            else -> false
        }

        if (!isAllowed) {
            // Если повар накормил без разрешения (оффлайн ошибка) - мы отклоняем транзакцию.
            // В будущем тут можно сохранять в таблицу FraudLog.
            throw RuntimeException("Нет разрешения в табеле на ${item.mealType}")
        }

        // 6. Сохранение
        val entity = MealTransactionEntity(
            // Если хэша нет (старая версия приложения), генерируем случайный
            transactionHash = item.transactionHash ?: UUID.randomUUID().toString(),
            timeStamp = item.timestamp,
            student = student,
            chef = chef,
            mealType = item.mealType,
            isOffline = true // Пакетная загрузка всегда считается оффлайн-синхронизацией
        )
        transactionRepository.save(entity)
    }
}