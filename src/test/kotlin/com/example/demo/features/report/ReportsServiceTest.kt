package com.example.demo.features.report

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.reports.service.ReportsService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@Import(ReportsService::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("ReportsService - Отчетность")
class ReportsServiceTest {

    @Autowired
    private lateinit var reportsService: ReportsService

    @Autowired
    private lateinit var transactionRepository: MealTransactionRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var student1: UserEntity
    private lateinit var student2: UserEntity
    private lateinit var chef: UserEntity

    @BeforeEach
    fun setup() {
        chef = userRepository.save(
            UserEntity(
                login = "chef",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Повар",
                surname = "Тестовый",
                fatherName = "Поварович"
            )
        )

        student1 = userRepository.save(
            UserEntity(
                login = "student1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент1",
                surname = "Первый",
                fatherName = "Студентович"
            )
        )

        student2 = userRepository.save(
            UserEntity(
                login = "student2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент2",
                surname = "Второй",
                fatherName = "Студентович"
            )
        )
    }

    @Test
    @DisplayName("Отчет за день без транзакций")
    fun `daily report with no transactions should return zeros`() {
        // Given
        val today = LocalDate.now()

        // When
        val report = reportsService.generateDailyReport(today)

        // Then
        assertEquals(0L, report.breakfastCount)
        assertEquals(0L, report.lunchCount)
        assertEquals(0L, report.dinnerCount)
        assertEquals(0L, report.totalCount)
        assertEquals(0L, report.offlineTransactions)
    }

    @Test
    @DisplayName("Отчет правильно считает количество студентов по типам еды")
    fun `daily report should count students by meal type`() {
        // Given
        val today = LocalDate.now()
        val now = LocalDateTime.now()

        // Студент1: завтрак + обед
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "hash1",
                timeStamp = now,
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "hash2",
                timeStamp = now,
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.LUNCH
            )
        )

        // Студент2: только обед
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "hash3",
                timeStamp = now,
                student = student2,
                chef = chef,
                isOffline = true,
                mealType = MealType.LUNCH
            )
        )

        // When
        val report = reportsService.generateDailyReport(today)

        // Then
        assertEquals(1L, report.breakfastCount, "Завтрак: 1 студент")
        assertEquals(2L, report.lunchCount, "Обед: 2 студента")
        assertEquals(0L, report.dinnerCount, "Ужин: 0 студентов")
        assertEquals(2L, report.totalCount, "Всего уникальных студентов: 2")
        assertEquals(1L, report.offlineTransactions, "Оффлайн транзакций: 1")
    }

    @Test
    @DisplayName("Один студент ест несколько раз - считается как 1 в totalCount")
    fun `same student eating multiple meals should count as 1 in total`() {
        // Given
        val today = LocalDate.now()
        val now = LocalDateTime.now()

        // Студент1 ест 3 раза
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "h1",
                timeStamp = now,
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "h2",
                timeStamp = now,
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.LUNCH
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "h3",
                timeStamp = now,
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.DINNER
            )
        )

        // When
        val report = reportsService.generateDailyReport(today)

        // Then
        assertEquals(3L, report.breakfastCount + report.lunchCount + report.dinnerCount)
        assertEquals(1L, report.totalCount, "Должен быть 1 уникальный студент")
    }

    @Test
    @DisplayName("Недельный отчет возвращает 7 дней")
    fun `weekly report should return 7 days`() {
        // Given
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)

        // When
        val report = reportsService.generateWeeklyReport(monday)

        // Then
        assertEquals(7, report.size, "Должно быть 7 дней")
        assertEquals(monday, report[0].date)
        assertEquals(monday.plusDays(6), report[6].date)
    }

    @Test
    @DisplayName("CSV экспорт генерирует правильный формат")
    fun `exportToCSV should generate valid CSV`() {
        // Given
        val today = LocalDate.now()
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "h1",
                timeStamp = LocalDateTime.now(),
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.LUNCH
            )
        )

        // When
        val csv = reportsService.exportToCSV(today, today)

        // Then
        assertTrue(csv.startsWith("Дата,Завтрак,Обед,Ужин"), "CSV должен начинаться с заголовков")
        assertTrue(csv.contains(today.toString()), "CSV должен содержать дату")
        assertTrue(csv.contains(",1,"), "CSV должен содержать данные об обеде")
    }

    @Test
    @DisplayName("CSV экспорт за период нескольких дней")
    fun `exportToCSV for date range should include all days`() {
        // Given
        val startDate = LocalDate.now().minusDays(2)
        val endDate = LocalDate.now()

        // When
        val csv = reportsService.exportToCSV(startDate, endDate)

        // Then
        val lines = csv.lines()
        assertEquals(4, lines.size, "Должно быть: заголовок + 3 дня данных")

        // Проверяем, что все даты присутствуют
        assertTrue(csv.contains(startDate.toString()))
        assertTrue(csv.contains(startDate.plusDays(1).toString()))
        assertTrue(csv.contains(endDate.toString()))
    }
}