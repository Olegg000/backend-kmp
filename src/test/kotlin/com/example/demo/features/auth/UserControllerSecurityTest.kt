package com.example.demo.features.auth

import com.example.demo.config.TestProfileResolver
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("UserController security")
class UserControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("GET /api/v1/auth/me без JWT -> 401")
    fun `me endpoint requires jwt`() {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("GET /api/v1/auth/my-keys без JWT -> 401")
    fun `my keys endpoint requires jwt`() {
        mockMvc.perform(get("/api/v1/auth/my-keys"))
            .andExpect(status().isUnauthorized)
    }
}
