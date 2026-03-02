package com.example.demo.features.notifications

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.notifications.service.NotificationService
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
import java.time.temporal.TemporalAdjusters

@DataJpaTest
@Import(NotificationService::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("NotificationService - напоминания по табелю")
class NotificationServiceTest(

    @Autowired private val notificationService: NotificationService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val permissionRepository: MealPermissionRepository
) {

    private lateinit var group: GroupEntity
    private lateinit var curator: UserEntity
    private lateinit var student: UserEntity

    @BeforeEach
    fun setup() {
        group = groupRepository.save(GroupEntity(groupName = "ПИ-21"))

        curator = userRepository.save(
            UserEntity(
                login = "curator-notif",
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

        student = userRepository.save(
            UserEntity(
                login = "student-notif",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
                group = group
            )
        )
    }

    @Test
    @DisplayName("Исключение, если куратор не найден")
    fun `should throw when curator not found`() {
        val ex = assertThrows(RuntimeException::class.java) {
            notificationService.checkCuratorRosterStatus("no-such-login")
        }
        assertTrue(ex.message!!.contains("Куратор не найден"))
    }

    @Test
    @DisplayName("Куратор без группы - нет напоминания, есть reason")
    fun `curator without group should return no reminder and reason`() {
        val curatorNoGroup = userRepository.save(
            UserEntity(
                login = "curator-no-group-notif",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Без",
                surname = "Группы",
                fatherName = "Групповнович"
            )
        )

        val result = notificationService.checkCuratorRosterStatus(curatorNoGroup.login)

        assertEquals(false, result["needsReminder"])
        val reason = result["reason"] as? String
        assertNotNull(reason)
        assertTrue(reason!!.contains("не привязан"))
    }

    @Test
    @DisplayName("Если на следующую неделю нет разрешений - needsReminder=true")
    fun `no permissions next week should require reminder`() {
        val result = notificationService.checkCuratorRosterStatus(curator.login)

        assertEquals(true, result["needsReminder"])
        assertTrue(result["daysUntilDeadline"] is Int)
        assertNotNull(result["deadlineDate"])
    }

    @Test
    @DisplayName("Если есть разрешение на следующую неделю - needsReminder=false")
    fun `permissions next week should disable reminder`() {
        val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val someDayNextWeek = nextMonday.plusDays(1)

        permissionRepository.save(
            MealPermissionEntity(
                date = someDayNextWeek,
                student = student,
                assignedBy = curator,
                reason = "Тест",
                isBreakfastAllowed = true,
                isLunchAllowed = false,
            )
        )

        val result = notificationService.checkCuratorRosterStatus(curator.login)

        assertEquals(false, result["needsReminder"])
    }
}
