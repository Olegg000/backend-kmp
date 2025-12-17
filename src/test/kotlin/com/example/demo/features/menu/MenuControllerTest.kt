package com.example.demo.features.menu

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.features.menu.dto.CreateMenuItemRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("MenuController - REST API")
class MenuControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val objectMapper: ObjectMapper
) {

    private fun chefToken(): String {
        val chef = userRepository.save(
            UserEntity(
                login = "chef-menu",
                passwordHash = "h",
                roles = mutableSetOf(Role.CHEF),
                name = "Мария",
                surname = "Поварова",
                fatherName = "Кулинаровна"
            )
        )
        return jwtUtils.generateToken(chef.login, chef.roles)
    }

    private fun studentToken(): String {
        val st = userRepository.save(
            UserEntity(
                login = "st-menu",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся"
            )
        )
        return jwtUtils.generateToken(st.login, st.roles)
    }

    @Test
    fun `GET menu returns 200`() {
        val token = studentToken() // или chefToken(), не важно, GET не ограничен по роли

        mockMvc.perform(
            get("/api/v1/menu")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `POST menu with chef token returns created item`() {
        val token = chefToken()
        val req = CreateMenuItemRequest(
            date = LocalDate.now(),
            name = "Суп",
            description = "Гороховый"
        )

        mockMvc.perform(
            post("/api/v1/menu")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Суп"))
    }

    @Test
    fun `POST menu with student token is forbidden`() {
        val token = studentToken()
        val req = CreateMenuItemRequest(
            date = LocalDate.now(),
            name = "Второе",
            description = "Описание"
        )

        mockMvc.perform(
            post("/api/v1/menu")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `POST menu without token returns 4xx`() {
        val req = CreateMenuItemRequest(
            date = LocalDate.now(),
            name = "Компот",
            description = "Фруктовый"
        )

        mockMvc.perform(
            post("/api/v1/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        )
            .andExpect(status().is4xxClientError)
    }
}