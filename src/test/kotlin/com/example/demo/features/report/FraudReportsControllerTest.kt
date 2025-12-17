package com.example.demo.features.report

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.SuspiciousTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.SuspiciousTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
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
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
class FraudReportsControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val suspiciousRepo: SuspiciousTransactionRepository
) {

    private fun adminToken(): String {
        val admin = userRepository.save(
            UserEntity(
                login = "admin-fraud",
                passwordHash = "h",
                roles = mutableSetOf(Role.ADMIN),
                name = "Admin",
                surname = "User",
                fatherName = "Test"
            )
        )
        return jwtUtils.generateToken(admin.login, admin.roles)
    }

    @Test
    fun `fraud csv export works`() {
        val token = adminToken()
        val today = LocalDate.now()

        mockMvc.perform(
            get("/api/v1/reports/fraud/export/csv")
                .header("Authorization", "Bearer $token")
                .param("startDate", today.toString())
                .param("endDate", today.toString())
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType("text/csv"))
    }

    @Test
    fun `fraud pdf export works`() {
        val token = adminToken()
        val today = LocalDate.now()

        mockMvc.perform(
            get("/api/v1/reports/fraud/export/pdf")
                .header("Authorization", "Bearer $token")
                .param("startDate", today.toString())
                .param("endDate", today.toString())
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect { result ->
                val bytes = result.response.contentAsByteArray
                assert(bytes.isNotEmpty()) { "PDF не должен быть пустым" }
            }
    }

    @Test
    fun `GET fraud with resolved=false returns only unresolved`() {
        val token = adminToken()
        val today = LocalDate.now()

        val student = userRepository.save(
            UserEntity(
                login = "st-fraud-1",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Иванов",
                fatherName = "Иванович"
            )
        )

        val unresolved = suspiciousRepo.save(
            SuspiciousTransactionEntity(
                student = student,
                chef = null,
                date = today,
                mealType = MealType.LUNCH,
                reason = "ALREADY_ATE",
                resolved = false
            )
        )

        suspiciousRepo.save(
            SuspiciousTransactionEntity(
                student = student,
                chef = null,
                date = today,
                mealType = MealType.LUNCH,
                reason = "ALREADY_ATE",
                resolved = true
            )
        )

        mockMvc.perform(
            get("/api/v1/reports/fraud")
                .header("Authorization", "Bearer $token")
                .param("startDate", today.toString())
                .param("endDate", today.toString())
                .param("resolved", "false")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(unresolved.id!!))
            .andExpect(jsonPath("$[0].resolved").value(false))
    }

    @Test
    fun `GET fraud with mealType filter returns only LUNCH`() {
        val token = adminToken()
        val today = LocalDate.now()

        val student = userRepository.save(
            UserEntity(
                login = "st-fraud-2",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Петр",
                surname = "Петров",
                fatherName = "Петрович"
            )
        )

        val lunch = suspiciousRepo.save(
            SuspiciousTransactionEntity(
                student = student,
                chef = null,
                date = today,
                mealType = MealType.LUNCH,
                reason = "ALREADY_ATE",
                resolved = false
            )
        )

        suspiciousRepo.save(
            SuspiciousTransactionEntity(
                student = student,
                chef = null,
                date = today,
                mealType = MealType.BREAKFAST,
                reason = "ALREADY_ATE",
                resolved = false
            )
        )

        mockMvc.perform(
            get("/api/v1/reports/fraud")
                .header("Authorization", "Bearer $token")
                .param("startDate", today.toString())
                .param("endDate", today.toString())
                .param("mealType", "LUNCH")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(lunch.id!!))
            .andExpect(jsonPath("$[0].mealType").value("LUNCH"))
    }
}