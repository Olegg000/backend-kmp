package com.example.demo.features.transactions

import com.example.demo.config.TestProfileResolver
import com.example.demo.config.TimeConfig
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.SuspiciousTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.transactions.dto.TransactionSyncItem
import com.example.demo.features.transactions.service.TransactionsService
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
import java.time.ZoneId
import java.util.UUID

@DataJpaTest
@Import(TransactionsService::class, TimeConfig::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("TransactionsService - Защита от двойного прохода")
class TransactionsServiceTest {

    @Autowired
    private lateinit var transactionsService: TransactionsService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionRepository: MealTransactionRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var suspiciousRepo: SuspiciousTransactionRepository

    private lateinit var student: UserEntity
    private lateinit var chef: UserEntity
    private lateinit var curator: UserEntity
    private lateinit var group: GroupEntity

    @BeforeEach
    fun setup() {
        // Создаем тестовую группу
        group = groupRepository.save(GroupEntity(groupName = "Test Group"))

        // Создаем куратора
        curator = userRepository.save(
            UserEntity(
                login = "curator-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Иван",
                surname = "Иванов",
                fatherName = "Иванович",
                group = group
            )
        )

        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        // Создаем студента
        student = userRepository.save(
            UserEntity(
                login = "student-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Петр",
                surname = "Петров",
                fatherName = "Петрович",
                group = group
            )
        )

        // Создаем повара
        chef = userRepository.save(
            UserEntity(
                login = "chef-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Мария",
                surname = "Кулинарова",
                fatherName = "Поварова"
            )
        )

        // Даем разрешение студенту на обед сегодня
        permissionRepository.save(
            MealPermissionEntity(
                date = LocalDate.now(),
                student = student,
                assignedBy = curator,
                reason = "Тестовое разрешение",
                isBreakfastAllowed = false,
                isLunchAllowed = true,
            )
        )
    }


    @Test
    @DisplayName("Подозрительная транзакция создается при повторной попытке")
    fun `double spending should create suspicious record`() {
        // Given — первая успешная транзакция
        val item = TransactionSyncItem(
            studentId = student.id!!,
            timestamp = LocalDateTime.now(),
            mealType = MealType.LUNCH,
            transactionHash = "tx-1"
        )
        transactionsService.syncBatch(chef.login, listOf(item))

        // When — повторная попытка
        val item2 = TransactionSyncItem(
            studentId = student.id!!,
            timestamp = LocalDateTime.now(),
            mealType = MealType.LUNCH,
            transactionHash = "tx-2"
        )
        transactionsService.syncBatch(chef.login, listOf(item2))

        // Then — в suspicious таблице должна появиться запись
        val all = suspiciousRepo.findAll()
        assertEquals(1, all.size)
        assertEquals(MealType.LUNCH, all[0].mealType)
        assertEquals("ALREADY_ATE", all[0].reason)
    }

    @Test
    @DisplayName("Успешная синхронизация транзакции с валидным разрешением")
    fun `syncBatch should succeed with valid permission`() {
        // Given
        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.LUNCH,
                transactionHash = UUID.randomUUID().toString()
            )
        )

        // When
        val response = transactionsService.syncBatch(chef.login, items)

        // Then
        assertEquals(1, response.successCount, "Должна быть 1 успешная транзакция")
        assertTrue(response.errors.isEmpty(), "Не должно быть ошибок")
        assertEquals(1, response.processed.size)
        assertEquals("SUCCESS", response.processed.first().status)

        // Проверяем, что транзакция сохранена
        val saved = transactionRepository.findAll()
        assertEquals(1, saved.size)
        assertEquals(student.id, saved[0].student.id)
        assertEquals(MealType.LUNCH, saved[0].mealType)
    }

    @Test
    @DisplayName("Защита от двойного прохода - отклонение повторной транзакции")
    fun `syncBatch should prevent double spending`() {
        // Given - первая транзакция
        val items1 = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.LUNCH,
                transactionHash = "unique-hash-1"
            )
        )
        transactionsService.syncBatch(chef.login, items1)

        // When - попытка пройти второй раз
        val items2 = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.LUNCH,
                transactionHash = "unique-hash-2" // Другой хеш!
            )
        )
        val response = transactionsService.syncBatch(chef.login, items2)

        // Then
        assertEquals(0, response.successCount, "Не должно быть успешных транзакций")
        assertEquals(1, response.errors.size, "Должна быть 1 ошибка")
        assertEquals(1, response.processed.size)
        assertEquals("FAILED", response.processed.first().status)
        assertTrue(
            response.errors[0].contains("уже получил"),
            "Ошибка должна говорить о повторной попытке"
        )
    }

    @Test
    @DisplayName("Отклонение транзакции без разрешения в табеле")
    fun `syncBatch should reject transaction without permission`() {
        // Given - студент без разрешения на завтрак
        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.BREAKFAST,
                transactionHash = UUID.randomUUID().toString()
            )
        )

        // When
        val response = transactionsService.syncBatch(chef.login, items)

        // Then
        assertEquals(0, response.successCount)
        assertEquals(1, response.errors.size)
        assertTrue(
            response.errors[0].contains("Нет разрешения"),
            "Ошибка должна говорить об отсутствии разрешения"
        )
    }

    @Test
    @DisplayName("Игнорирование дублей по хешу транзакции")
    fun `syncBatch should ignore duplicate transaction hashes`() {
        // Given
        val hash = "duplicate-hash-123"
        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.LUNCH,
                transactionHash = hash
            )
        )

        // When - первая попытка
        val response1 = transactionsService.syncBatch(chef.login, items)

        // When - повторная отправка того же пакета
        val response2 = transactionsService.syncBatch(chef.login, items)

        // Then
        assertEquals(1, response1.successCount, "Первая транзакция должна пройти")
        assertEquals(1, response2.successCount, "Вторая должна быть проигнорирована (не ошибка)")

        // Но в БД должна быть только 1 запись
        val saved = transactionRepository.findAll()
        assertEquals(1, saved.size, "Должна быть только одна транзакция")
    }

    @Test
    @DisplayName("Синхронизация поддерживает timestampEpochSec без legacy timestamp")
    fun `syncBatch should accept epoch timestamp`() {
        val businessZone = ZoneId.of("Europe/Samara")
        val mealDateTime = LocalDate.now(businessZone).atTime(12, 0)
        val epoch = mealDateTime.atZone(businessZone).toEpochSecond()

        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                mealType = MealType.LUNCH,
                transactionHash = "epoch-only-hash",
                timestamp = null,
                timestampEpochSec = epoch,
            )
        )

        val response = transactionsService.syncBatch(chef.login, items)

        assertEquals(1, response.successCount, "Epoch-based timestamp должен успешно обрабатываться")
        assertTrue(response.errors.isEmpty())
    }

    @Test
    @DisplayName("timestampEpochSec имеет приоритет над legacy timestamp")
    fun `syncBatch should prioritize epoch timestamp over legacy`() {
        val businessZone = ZoneId.of("Europe/Samara")
        val today = LocalDate.now(businessZone).atTime(13, 0)
        val yesterday = today.minusDays(1)
        val epochToday = today.atZone(businessZone).toEpochSecond()

        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                mealType = MealType.LUNCH,
                transactionHash = "epoch-priority-hash",
                timestamp = yesterday, // Для вчера нет разрешения в табеле
                timestampEpochSec = epochToday, // Для сегодня разрешение есть
            )
        )

        val response = transactionsService.syncBatch(chef.login, items)

        assertEquals(1, response.successCount, "При наличии epoch поле legacy должно игнорироваться")
        assertTrue(response.errors.isEmpty())
    }

    @Test
    @DisplayName("Обработка пакета с несколькими студентами")
    fun `syncBatch should handle multiple students`() {
        // Given - второй студент
        val student2 = userRepository.save(
            UserEntity(
                login = "student2-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Анна",
                surname = "Сидорова",
                fatherName = "Петровна",
                group = group
            )
        )

        // Разрешение для второго студента
        permissionRepository.save(
            MealPermissionEntity(
                date = LocalDate.now(),
                student = student2,
                assignedBy = curator,
                reason = "Тест",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
            )
        )

        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                mealType = MealType.LUNCH,
                transactionHash = "hash1",
                timestamp = LocalDateTime.now(),
            ),
            TransactionSyncItem(
                studentId = student2.id!!,
                mealType = MealType.LUNCH,
                transactionHash = "hash2",
                timestamp = LocalDateTime.now(),
            ),
        )

        // When
        val response = transactionsService.syncBatch(chef.login, items)

        // Then
        assertEquals(2, response.successCount, "Обе транзакции должны пройти")
        assertTrue(response.errors.isEmpty())
    }

    @Test
    @DisplayName("Частичный успех - один проходит, другой отклоняется")
    fun `syncBatch should handle partial success`() {
        // Given
        val student2 = userRepository.save(
            UserEntity(
                login = "student3-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Сергей",
                surname = "Смирнов",
                fatherName = "Иванович",
                group = group
            )
        )
        // У student2 НЕТ разрешения

        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                mealType = MealType.LUNCH,
                transactionHash = "hash1",
                timestamp = LocalDateTime.now(),
            ),
            TransactionSyncItem(
                studentId = student2.id!!,
                mealType = MealType.LUNCH,
                transactionHash = "hash2",
                timestamp = LocalDateTime.now(),
            ),
        )

        // When
        val response = transactionsService.syncBatch(chef.login, items)

        // Then
        assertEquals(1, response.successCount, "Первый студент должен пройти")
        assertEquals(1, response.errors.size, "Второй должен быть отклонен")
    }

    @Test
    @DisplayName("MANY_CHILDREN не может получить второй прием пищи в тот же день")
    fun `many children student cannot consume two meals in one day`() {
        val baseTs = LocalDate.now().atTime(12, 0)

        student.studentCategory = StudentCategory.MANY_CHILDREN
        userRepository.save(student)

        // Разрешаем оба приема на день, ограничение должно сработать на уровне транзакций.
        val permission = permissionRepository.findByStudentAndDate(student, LocalDate.now())!!
        permission.isBreakfastAllowed = true
        permission.isLunchAllowed = true
        permissionRepository.save(permission)

        val first = transactionsService.syncBatch(
            chef.login,
            listOf(
                TransactionSyncItem(
                    studentId = student.id!!,
                    timestamp = baseTs.minusHours(1),
                    mealType = MealType.BREAKFAST,
                    transactionHash = "many-children-1"
                )
            )
        )
        assertEquals(1, first.successCount)

        val second = transactionsService.syncBatch(
            chef.login,
            listOf(
                TransactionSyncItem(
                    studentId = student.id!!,
                    timestamp = baseTs.plusHours(2),
                    mealType = MealType.LUNCH,
                    transactionHash = "many-children-2"
                )
            )
        )

        assertEquals(0, second.successCount)
        assertEquals(1, second.errors.size)
        assertTrue(second.errors.first().contains("Многодетные"))
    }
}
