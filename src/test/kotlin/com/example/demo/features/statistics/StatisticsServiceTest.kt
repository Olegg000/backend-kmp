package com.example.demo.features.statistics

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.statistics.service.StatisticsService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@Import(StatisticsService::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("StatisticsService - статистика по питанию группы")
class StatisticsServiceTest(

    @Autowired private val statisticsService: StatisticsService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val transactionRepository: MealTransactionRepository
) {

    private lateinit var group: GroupEntity
    private lateinit var curator: UserEntity
    private lateinit var student1: UserEntity
    private lateinit var student2: UserEntity
    private lateinit var chef: UserEntity

    @BeforeEach
    fun setup() {
        group = groupRepository.save(GroupEntity(groupName = "ИСП-21"))

        curator = userRepository.save(
            UserEntity(
                login = "curator-stat",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Руководителевна",
                group = group
            )
        )
        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        student1 = userRepository.save(
            UserEntity(
                login = "student1-stat",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
                group = group
            )
        )

        student2 = userRepository.save(
            UserEntity(
                login = "student2-stat",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Петр",
                surname = "Обучаемый",
                fatherName = "Педагогович",
                group = group
            )
        )

        chef = userRepository.save(
            UserEntity(
                login = "chef-stat",
                passwordHash = "h",
                roles = mutableSetOf(Role.CHEF),
                name = "Мария",
                surname = "Поварова",
                fatherName = "Кулинаровна"
            )
        )
    }

    @Test
    @DisplayName("Куратор видит всех студентов своей группы с false без транзакций")
    fun `getGroupMealStatus returns all students with false when no transactions`() {
        val today = LocalDate.now()

        val stats = statisticsService.getGroupMealStatus(curator.login, today)

        assertEquals(2, stats.size)
        val s1 = stats.find { it.studentId == student1.id }!!
        val s2 = stats.find { it.studentId == student2.id }!!

        listOf(s1, s2).forEach { st ->
            assertFalse(st.hadBreakfast)
            assertFalse(st.hadLunch)
        }
    }

    @Test
    @DisplayName("Флаги hadBreakfast/hadLunch/... выставляются правильно по транзакциям")
    fun `getGroupMealStatus sets flags correctly based on transactions`() {
        val today = LocalDate.now()
        val now = LocalDateTime.now()

        // Студент1: завтрак + обед
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "t1",
                timeStamp = now,
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "t2",
                timeStamp = now,
                student = student1,
                chef = chef,
                isOffline = false,
                mealType = MealType.LUNCH
            )
        )

        // Студент2: ужин + полдник + спец
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "t3",
                timeStamp = now,
                student = student2,
                chef = chef,
                isOffline = true,
                mealType = MealType.LUNCH
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "t4",
                timeStamp = now,
                student = student2,
                chef = chef,
                isOffline = true,
                mealType = MealType.LUNCH
            )
        )
        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "t5",
                timeStamp = now,
                student = student2,
                chef = chef,
                isOffline = true,
                mealType = MealType.LUNCH
            )
        )

        val stats = statisticsService.getGroupMealStatus(curator.login, today)
        val s1 = stats.find { it.studentId == student1.id }!!
        val s2 = stats.find { it.studentId == student2.id }!!

        assertTrue(s1.hadBreakfast)
        assertTrue(s1.hadLunch)

        assertFalse(s1 == s2)

        assertFalse(s2.hadBreakfast)
        assertTrue(s2.hadLunch)
    }

    @Test
    @DisplayName("Исключение, если куратор не привязан к группе")
    fun `getGroupMealStatus throws if curator has no group`() {
        val curatorNoGroup = userRepository.save(
            UserEntity(
                login = "curator-no-group-stat",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Без",
                surname = "Группы",
                fatherName = "Групповнович"
            )
        )

        val ex = assertThrows(BusinessException::class.java) {
            statisticsService.getGroupMealStatus(curatorNoGroup.login, LocalDate.now())
        }
        assertEquals("CURATOR_GROUP_ACCESS_DENIED", ex.code)
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
    }
}
