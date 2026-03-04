package com.example.demo.features.reports.controller

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.logging.RequestCorrelationFilter
import com.example.demo.core.security.JwtUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
class ReportsRequestIdControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val objectMapper: ObjectMapper
) {

    @Test
    fun `consumption report success response includes request id header`() {
        val token = adminToken()
        val today = LocalDate.now().toString()

        mockMvc.perform(
            get("/api/v1/reports/consumption")
                .header("Authorization", "Bearer $token")
                .param("startDate", today)
                .param("endDate", today)
                .param("assignedByRole", "ALL")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists(RequestCorrelationFilter.REQUEST_ID_HEADER))
            .andExpect {
                val requestId = it.response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)
                assertFalse(requestId.isNullOrBlank(), "X-Request-Id must be present on successful response")
            }
    }

    @Test
    fun `consumption report error response keeps same request id in header and body`() {
        val token = adminToken()
        val startDate = LocalDate.now()
        val endDate = startDate.minusDays(1)

        mockMvc.perform(
            get("/api/v1/reports/consumption")
                .header("Authorization", "Bearer $token")
                .param("startDate", startDate.toString())
                .param("endDate", endDate.toString())
                .param("assignedByRole", "ALL")
        )
            .andExpect(status().isBadRequest)
            .andExpect(header().exists(RequestCorrelationFilter.REQUEST_ID_HEADER))
            .andExpect {
                val headerRequestId = it.response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)
                val bodyRequestId = objectMapper.readTree(it.response.contentAsString)
                    .path("requestId")
                    .asText(null)
                assertFalse(headerRequestId.isNullOrBlank(), "X-Request-Id must be present on error response")
                assertFalse(bodyRequestId.isNullOrBlank(), "AppError.requestId must be present on error response")
                assertEquals(headerRequestId, bodyRequestId, "Header request id must match AppError.requestId")
            }
    }

    private fun adminToken(): String {
        val admin = userRepository.save(
            UserEntity(
                login = "admin-rid-${java.util.UUID.randomUUID()}",
                passwordHash = "h",
                roles = mutableSetOf(Role.ADMIN),
                name = "Admin",
                surname = "User",
                fatherName = "Test"
            )
        )
        return jwtUtils.generateToken(admin.login, admin.roles)
    }
}
