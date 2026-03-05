package com.example.demo.features.reports.controller

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
class ReportsConsumptionControllerSmokeTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val jwtUtils: JwtUtils,
) {

    @Test
    fun `consumption report returns 200 outside test transaction`() {
        val group = groupRepository.save(GroupEntity(groupName = "SMOKE-${UUID.randomUUID()}"))
        userRepository.save(
            UserEntity(
                login = "student-smoke-${UUID.randomUUID()}",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Smoke",
                surname = "Student",
                fatherName = "Test",
                group = group,
            )
        )
        val admin = userRepository.save(
            UserEntity(
                login = "admin-smoke-${UUID.randomUUID()}",
                passwordHash = "h",
                roles = mutableSetOf(Role.ADMIN),
                name = "Smoke",
                surname = "Admin",
                fatherName = "Test",
            )
        )
        val token = jwtUtils.generateToken(admin.login, admin.roles)
        val today = LocalDate.now().toString()

        mockMvc.perform(
            get("/api/v1/reports/consumption")
                .header("Authorization", "Bearer $token")
                .param("startDate", today)
                .param("endDate", today)
                .param("assignedByRole", "ALL")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect {
                assertFalse(it.response.contentAsString.isBlank(), "Ответ consumption отчета не должен быть пустым")
            }
    }

    @Test
    fun `consumption report rejects future end date`() {
        val admin = userRepository.save(
            UserEntity(
                login = "admin-future-${UUID.randomUUID()}",
                passwordHash = "h",
                roles = mutableSetOf(Role.ADMIN),
                name = "Future",
                surname = "Admin",
                fatherName = "Test",
            )
        )
        val token = jwtUtils.generateToken(admin.login, admin.roles)
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        mockMvc.perform(
            get("/api/v1/reports/consumption")
                .header("Authorization", "Bearer $token")
                .param("startDate", today.toString())
                .param("endDate", tomorrow.toString())
                .param("assignedByRole", "ALL")
        )
            .andExpect(status().isBadRequest)
            .andExpect {
                assertTrue(
                    it.response.contentAsString.contains("FUTURE_REPORT_DATE_NOT_ALLOWED"),
                    "Должен вернуться код FUTURE_REPORT_DATE_NOT_ALLOWED"
                )
            }
    }
}
