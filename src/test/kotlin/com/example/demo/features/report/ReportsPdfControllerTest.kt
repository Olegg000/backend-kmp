package com.example.demo.features.reports.controller

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
class ReportsPdfControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val jwtUtils: JwtUtils
) {

    @Test
    fun `export pdf returns application pdf`() {
        val admin = userRepository.save(
            UserEntity(
                login = "admin-pdf-${java.util.UUID.randomUUID()}",
                passwordHash = "h",
                roles = mutableSetOf(Role.ADMIN),
                name = "Admin",
                surname = "User",
                fatherName = "Test"
            )
        )
        val token = jwtUtils.generateToken(admin.login, admin.roles)

        val today = LocalDate.now()

        mockMvc.perform(
            get("/api/v1/reports/export/pdf")
                .header("Authorization", "Bearer $token")
                .param("startDate", today.toString())
                .param("endDate", today.toString())
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect {
                val bytes = it.response.contentAsByteArray
                assert(bytes.isNotEmpty()) { "PDF не должен быть пустым" }
            }
    }
}