package com.example.demo.features.student

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
import com.example.demo.features.student.service.StudentService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@Import(StudentService::class, BCryptPasswordEncoder::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("StudentService - Consumption Tracking Tests")
class StudentServiceTest {

    @Autowired
    private lateinit var studentService: StudentService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    @Autowired
    private lateinit var transactionRepository: MealTransactionRepository

    private lateinit var student: UserEntity
    private lateinit var chef: UserEntity
    private lateinit var curator: UserEntity

    @BeforeEach
    fun setup() {
        val group = groupRepository.save(GroupEntity(groupName = "Test Group", curator = null))

        curator = userRepository.save(
            UserEntity(
                login = "curator-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Куратор",
                surname = "Тестовый",
                fatherName = "Кураторович"
            )
        )

        student = userRepository.save(
            UserEntity(
                login = "student-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент",
                surname = "Тестовый",
                fatherName = "Студентович",
                group = group,
                publicKey = "test-key"
            )
        )

        chef = userRepository.save(
            UserEntity(
                login = "chef-test",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Повар",
                surname = "Тестовый",
                fatherName = "Поварович"
            )
        )

        // Даем разрешения на все приемы пищи
        permissionRepository.save(
            MealPermissionEntity(
                date = LocalDate.now(),
                student = student,
                assignedBy = curator,
                reason = "Тест",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
                isDinnerAllowed = true,
                isSnackAllowed = true,
                isSpecialAllowed = true
            )
        )
    }

    @Test
    @DisplayName("Получение статуса питания: нет транзакций")
    fun `getTodayMeals should return all allowed and none consumed when no transactions exist`() {
        // When
        val result = studentService.getTodayMeals(student.login)

        // Then
        assertEquals(LocalDate.now(), result.date)
        assertTrue(result.isBreakfastAllowed)
        assertTrue(result.isLunchAllowed)
        assertTrue(result.isDinnerAllowed)
        assertTrue(result.isSnackAllowed)
        assertTrue(result.isSpecialAllowed)
        
        assertFalse(result.isBreakfastConsumed)
        assertFalse(result.isLunchConsumed)
        assertFalse(result.isDinnerConsumed)
        assertFalse(result.isSnackConsumed)
        assertFalse(result.isSpecialConsumed)
    }

    @Test
    @DisplayName("Получение статуса питания: завтрак использован")
    fun `getTodayMeals should mark breakfast as consumed after transaction`() {
        // Given - создаем транзакцию завтрака
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "test-hash-1",
                timeStamp = LocalDateTime.now(),
                student = student,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST
            )
        )

        // When
        val result = studentService.getTodayMeals(student.login)

        // Then
        assertTrue(result.isBreakfastConsumed, "Завтрак должен быть помечен как использованный")
        assertFalse(result.isLunchConsumed, "Обед не должен быть помечен")
        assertFalse(result.isDinnerConsumed, "Ужин не должен быть помечен")
    }

    @Test
    @DisplayName("Получение статуса питания: несколько приемов пищи использованы")
    fun `getTodayMeals should mark multiple meals as consumed`() {
        // Given - создаем транзакции завтрака и обеда
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "test-hash-breakfast",
                timeStamp = LocalDateTime.now().minusHours(3),
                student = student,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "test-hash-lunch",
                timeStamp = LocalDateTime.now(),
                student = student,
                chef = chef,
                isOffline = false,
                mealType = MealType.LUNCH
            )
        )

        // When
        val result = studentService.getTodayMeals(student.login)

        // Then
        assertTrue(result.isBreakfastConsumed)
        assertTrue(result.isLunchConsumed)
        assertFalse(result.isDinnerConsumed)
        assertFalse(result.isSnackConsumed)
        assertFalse(result.isSpecialConsumed)
    }

    @Test
    @DisplayName("Получение статуса питания: транзакции из прошлого не учитываются")
    fun `getTodayMeals should not count yesterday transactions`() {
        // Given - создаем транзакцию вчерашнего дня
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "test-hash-yesterday",
                timeStamp = LocalDateTime.now().minusDays(1),
                student = student,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST
            )
        )

        // When
        val result = studentService.getTodayMeals(student.login)

        // Then - вчерашняя транзакция не должна влиять на сегодняшний статус
        assertFalse(result.isBreakfastConsumed, "Вчерашний завтрак не должен влиять на сегодня")
    }
}
