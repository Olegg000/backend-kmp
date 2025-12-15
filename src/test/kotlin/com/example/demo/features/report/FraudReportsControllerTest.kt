package com.example.demo.features.report

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.UserEntity
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
    @Autowired private val jwtUtils: JwtUtils
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
}