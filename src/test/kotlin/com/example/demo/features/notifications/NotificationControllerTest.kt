package com.example.demo.features.notifications

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("NotificationController - REST API")
class NotificationControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val permissionRepository: MealPermissionRepository
) {

    @Test
    @DisplayName("GET roster-deadline для не-куратора -> 403")
    fun `GET roster-deadline should forbid student role`() {
        val student = userRepository.save(
            UserEntity(
                login = "student-notif-forbidden",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
            )
        )
        val token = jwtUtils.generateToken(student.login, student.roles)

        mockMvc.perform(
            get("/api/v1/notifications/roster-deadline")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET roster-deadline returns JSON for curator`() {
        val group = groupRepository.save(GroupEntity(groupName = "ПИ-21"))

        val curator = userRepository.save(
            UserEntity(
                login = "curator-notif-ctrl",
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

        val token = jwtUtils.generateToken(curator.login, curator.roles)

        mockMvc.perform(
            get("/api/v1/notifications/roster-deadline")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.needsReminder").exists())
            .andExpect(jsonPath("$.daysUntilDeadline").exists())
    }

    @Test
    @DisplayName("Если на следующую неделю есть разрешение — needsReminder=false")
    fun `GET roster-deadline returns no reminder when next week has permissions`() {
        val group = groupRepository.save(GroupEntity(groupName = "ПИ-22"))

        val curator = userRepository.save(
            UserEntity(
                login = "curator-notif-ctrl-2",
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

        val student = userRepository.save(
            UserEntity(
                login = "student-notif-ctrl",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
                group = group
            )
        )

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

        val token = jwtUtils.generateToken(curator.login, curator.roles)

        mockMvc.perform(
            get("/api/v1/notifications/roster-deadline")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.needsReminder").value(false))
    }
}
