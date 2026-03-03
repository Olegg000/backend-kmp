package com.example.demo.features.roster

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.exception.BusinessException
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.roster.dto.UpdateRosterRequest
import com.example.demo.features.roster.service.RosterService
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

@DataJpaTest
@Import(RosterService::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("RosterService - Управление табелем питания")
class RosterServiceTest {

    @Autowired
    private lateinit var rosterService: RosterService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var permissionRepository: MealPermissionRepository

    private lateinit var curator: UserEntity
    private lateinit var student1: UserEntity
    private lateinit var student2: UserEntity
    private lateinit var group: GroupEntity

    @BeforeEach
    fun setup() {
        // Создаем группу
        group = groupRepository.save(GroupEntity(groupName = "ИСП-21"))

        // Создаем куратора
        curator = userRepository.save(
            UserEntity(
                login = "curator1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Руководителевна",
                group = group
            )
        )

        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        // Создаем студентов
        student1 = userRepository.save(
            UserEntity(
                login = "student1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
                group = group,
                studentCategory = StudentCategory.SVO
            )
        )

        student2 = userRepository.save(
            UserEntity(
                login = "student2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Петр",
                surname = "Обучаемый",
                fatherName = "Педагогович",
                group = group,
                studentCategory = StudentCategory.SVO
            )
        )
    }

    @Test
    @DisplayName("Куратор видит всех студентов своей группы")
    fun `getRosterForGroup should return all students in curator group`() {
        // Given
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)

        // When
        val roster = rosterService.getRosterForGroup(curator.login, monday)

        // Then
        assertEquals(2, roster.size, "Должно быть 2 студента")
        assertTrue(roster.any { it.studentId == student1.id })
        assertTrue(roster.any { it.studentId == student2.id })

        // Проверяем, что для каждого студента есть 5 дней
        roster.forEach { row ->
            assertEquals(5, row.days.size, "Должно быть 5 дней (пн-пт)")
        }
    }

    @Test
    @DisplayName("Изначально все разрешения должны быть false")
    fun `initial roster should have all permissions set to false`() {
        // Given
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)

        // When
        val roster = rosterService.getRosterForGroup(curator.login, monday)

        // Then
        roster.forEach { studentRow ->
            studentRow.days.forEach { day ->
                assertFalse(day.isBreakfast, "Завтрак должен быть выключен")
                assertFalse(day.isLunch, "Обед должен быть выключен")
            }
        }
    }

    @Test
    @DisplayName("Обновление разрешений студента работает корректно")
    fun `updateRoster should save permissions correctly`() {
        // Given
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val permissions = listOf(
            DayPermissionDto(monday, true, true, "Учебный день"),
            DayPermissionDto(monday.plusDays(1), true, true, "Полный день")
        )

        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = permissions
        )

        // When
        rosterService.updateRoster(request, curator.login)

        // Then
        val roster = rosterService.getRosterForGroup(curator.login, monday)
        val student1Row = roster.find { it.studentId == student1.id }!!

        // Проверяем понедельник
        val mondayPerms = student1Row.days[0]
        assertTrue(mondayPerms.isBreakfast)
        assertTrue(mondayPerms.isLunch)

        // Проверяем вторник
        val tuesdayPerms = student1Row.days[1]
        assertTrue(tuesdayPerms.isBreakfast)
        assertTrue(tuesdayPerms.isLunch)
    }

    @Test
    @DisplayName("Обновление разрешений не затрагивает других студентов")
    fun `updateRoster should not affect other students`() {
        // Given
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val permissions = listOf(
            DayPermissionDto(monday, true, true, "Тест")
        )

        val request = UpdateRosterRequest(student1.id!!, permissions)

        // When
        rosterService.updateRoster(request, curator.login)

        // Then
        val roster = rosterService.getRosterForGroup(curator.login, monday)
        val student2Row = roster.find { it.studentId == student2.id }!!

        // У второго студента все должно остаться false
        student2Row.days.forEach { day ->
            assertFalse(day.isBreakfast)
            assertFalse(day.isLunch)
        }
    }

    @Test
    @DisplayName("Снятие всех галочек удаляет запись из БД")
    fun `updateRoster with all false should delete permission`() {
        // Given - сначала создаем разрешение
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val createRequest = UpdateRosterRequest(
            student1.id!!,
            listOf(DayPermissionDto(monday, true, true, null))
        )
        rosterService.updateRoster(createRequest, curator.login)

        // When - снимаем все галочки
        val deleteRequest = UpdateRosterRequest(
            student1.id!!,
            listOf(DayPermissionDto(monday, false, false, null))
        )
        rosterService.updateRoster(deleteRequest, curator.login)

        // Then
        val saved = permissionRepository.findByStudentAndDate(student1, monday)
        assertNull(saved, "Запись должна быть удалена из БД")
    }

    @Test
    @DisplayName("Исключение, если куратор не привязан к группе")
    fun `getRosterForGroup should throw if curator has no group`() {
        // Given - куратор без группы
        val curatorNoGroup = userRepository.save(
            UserEntity(
                login = "curator-no-group",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Без",
                surname = "Группы",
                fatherName = "Групповнович"
            )
        )

        // When & Then
        val exception = assertThrows(RuntimeException::class.java) {
            rosterService.getRosterForGroup(curatorNoGroup.login, LocalDate.now())
        }

        assertTrue(exception.message!!.contains("не привязан"))
    }

    @Test
    @DisplayName("Назначение питания без категории блокируется с кодом STUDENT_CATEGORY_REQUIRED")
    fun `updateRoster should fail when student has no category`() {
        student1.studentCategory = null
        userRepository.save(student1)

        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val request = UpdateRosterRequest(
            studentId = student1.id!!,
            permissions = listOf(DayPermissionDto(monday, true, false, "Тест"))
        )

        val ex = assertThrows(BusinessException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }

        assertEquals("STUDENT_CATEGORY_REQUIRED", ex.code)
    }

    @Test
    @DisplayName("Куратор не может изменять табель студента чужой группы")
    fun `updateRoster should fail for foreign student`() {
        val foreignGroup = groupRepository.save(GroupEntity(groupName = "ИСП-22"))
        val foreignCurator = userRepository.save(
            UserEntity(
                login = "curator2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Ольга",
                surname = "Вторая",
                fatherName = "Кураторовна"
            )
        )
        foreignGroup.curators = mutableSetOf(foreignCurator)
        groupRepository.save(foreignGroup)

        val foreignStudent = userRepository.save(
            UserEntity(
                login = "student-foreign",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Сергей",
                surname = "Чужой",
                fatherName = "Иванович",
                group = foreignGroup,
                studentCategory = StudentCategory.SVO
            )
        )

        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val request = UpdateRosterRequest(
            studentId = foreignStudent.id!!,
            permissions = listOf(DayPermissionDto(monday, true, false, "Тест"))
        )

        val ex = assertThrows(RuntimeException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }

        assertTrue(ex.message!!.contains("только студентов своих групп"))
    }
}
