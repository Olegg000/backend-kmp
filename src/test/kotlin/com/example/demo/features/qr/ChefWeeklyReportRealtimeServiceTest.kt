package com.example.demo.features.qr

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.database.repository.WeeklyReportSnapshotRepository
import com.example.demo.features.qr.service.ChefDataService
import com.example.demo.features.roster.service.RosterWeekPolicy
import com.example.demo.features.roster.service.WeeklyRosterFreezeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@DataJpaTest
@Import(
    ChefDataService::class,
    WeeklyRosterFreezeService::class,
    RosterWeekPolicy::class,
    ChefWeeklyReportRealtimeServiceTest.FixedThursdayClockConfig::class,
)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("Chef weekly report - realtime before lock")
class ChefWeeklyReportRealtimeServiceTest(
    @Autowired private val chefDataService: ChefDataService,
    @Autowired private val rosterWeekPolicy: RosterWeekPolicy,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val mealPermissionRepository: MealPermissionRepository,
    @Autowired private val weeklyReportSnapshotRepository: WeeklyReportSnapshotRepository,
) {

    @TestConfiguration
    class FixedThursdayClockConfig {
        @Bean
        fun businessZoneId(): ZoneId = ZoneId.of("Europe/Samara")

        @Bean
        fun businessClock(zoneId: ZoneId): Clock =
            Clock.fixed(Instant.parse("2026-03-05T07:00:00Z"), zoneId) // 10:00 Самара (до дедлайна)
    }

    private lateinit var weekStart: LocalDate
    private lateinit var chef: UserEntity

    @BeforeEach
    fun setup() {
        val group = groupRepository.save(GroupEntity(groupName = "ИСП-RT"))
        val curator = userRepository.save(
            UserEntity(
                login = "curator-weekly-rt",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Р",
                group = group,
            )
        )
        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        chef = userRepository.save(
            UserEntity(
                login = "chef-weekly-rt",
                passwordHash = "h",
                roles = mutableSetOf(Role.CHEF),
                name = "Повар",
                surname = "Реалтаймов",
                fatherName = "Т",
            )
        )

        val student = userRepository.save(
            UserEntity(
                login = "student-weekly-rt",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "И",
                group = group,
                studentCategory = StudentCategory.SVO,
            )
        )

        weekStart = rosterWeekPolicy.nextWeekStart()
        mealPermissionRepository.save(
            MealPermissionEntity(
                date = weekStart,
                student = student,
                assignedBy = curator,
                reason = "Тест",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
            )
        )
        mealPermissionRepository.save(
            MealPermissionEntity(
                date = weekStart.plusDays(1),
                student = student,
                assignedBy = curator,
                reason = "Тест",
                isBreakfastAllowed = true,
                isLunchAllowed = false,
            )
        )
    }

    @Test
    fun `next week report is built from live permissions before lock`() {
        assertTrue(weeklyReportSnapshotRepository.findAllByWeekStartOrderByDateAsc(weekStart).isEmpty())

        val report = chefDataService.getWeeklyReport(chef.login, weekStart)

        assertEquals(5, report.days.size)
        assertEquals(2, report.totalBreakfastCount)
        assertEquals(1, report.totalLunchCount)
        assertEquals(1, report.totalBothCount)
        assertFalse(report.confirmed)
        assertTrue(weeklyReportSnapshotRepository.findAllByWeekStartOrderByDateAsc(weekStart).isEmpty())
    }
}
