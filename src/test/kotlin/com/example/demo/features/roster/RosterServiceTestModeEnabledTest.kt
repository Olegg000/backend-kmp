package com.example.demo.features.roster

import com.example.demo.config.TestProfileResolver
import com.example.demo.config.TimeConfig
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.notifications.service.NotificationService
import com.example.demo.features.roster.dto.DayPermissionDto
import com.example.demo.features.roster.dto.UpdateRosterRequest
import com.example.demo.features.roster.service.RosterService
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@DataJpaTest
@Import(RosterService::class, RosterWeekPolicy::class, NotificationService::class, TimeConfig::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@TestPropertySource(properties = ["app.test-mode.enabled=true"])
@DisplayName("RosterService - test-mode")
class RosterServiceTestModeEnabledTest {

    @Autowired
    private lateinit var rosterService: RosterService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    private lateinit var curator: UserEntity
    private lateinit var student: UserEntity

    @BeforeEach
    fun setup() {
        val group = groupRepository.save(GroupEntity(groupName = "ТЕСТ-TEST-MODE"))

        curator = userRepository.save(
            UserEntity(
                login = "curator-test-mode",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Куратор",
                fatherName = "Тестовна",
                group = group,
            )
        )

        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        student = userRepository.save(
            UserEntity(
                login = "student-test-mode",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студент",
                fatherName = "Тестович",
                group = group,
                studentCategory = StudentCategory.SVO,
            )
        )
    }

    @Test
    @DisplayName("Текущая неделя доступна для чтения")
    fun `current week should be readable`() {
        val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val rows = rosterService.getRosterForGroup(curator.login, thisMonday)

        assertEquals(1, rows.size)
        assertEquals(student.id, rows.first().studentId)
    }

    @Test
    @DisplayName("Текущая неделя в test-mode доступна только для просмотра")
    fun `current week should be read-only in test mode`() {
        val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val request = UpdateRosterRequest(
            studentId = student.id!!,
            permissions = listOf(
                DayPermissionDto(
                    date = thisMonday,
                    isBreakfast = true,
                    isLunch = false,
                    reason = "Тест режима",
                )
            )
        )

        val ex = assertThrows(com.example.demo.core.exception.BusinessException::class.java) {
            rosterService.updateRoster(request, curator.login)
        }
        assertEquals("ROSTER_ONLY_NEXT_WEEK_OR_LATER", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }
}
