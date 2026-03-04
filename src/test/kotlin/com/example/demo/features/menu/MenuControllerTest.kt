package com.example.demo.features.menu

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.MenuEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MenuRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.features.menu.dto.CreateMenuItemRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest
@AutoConfigureMockMvc
@Import(MenuControllerTest.FixedClockConfig::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("MenuController - REST API")
class MenuControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val menuRepository: MenuRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val objectMapper: ObjectMapper
) {
    @TestConfiguration
    class FixedClockConfig {
        @Bean
        @Primary
        fun fixedBusinessClock(): Clock = Clock.fixed(
            Instant.parse("2026-03-04T20:30:00Z"), // 2026-03-05 00:30 Europe/Samara
            ZoneId.of("Europe/Samara"),
        )
    }


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
    fun `GET menu without date uses business clock day`() {
        val token = studentToken()
        menuRepository.save(
            MenuEntity(
                date = LocalDate.of(2026, 3, 5),
                name = "По Samara",
                location = "Корпус А",
                description = "Тест",
            )
        )
        menuRepository.save(
            MenuEntity(
                date = LocalDate.of(2026, 3, 4),
                name = "По UTC",
                location = "Корпус А",
                description = "Не должно попасть",
            )
        )

        mockMvc.perform(
            get("/api/v1/menu")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].date").value("2026-03-05"))
            .andExpect(jsonPath("$[0].name").value("По Samara"))
    }

    @Test
    fun `POST menu with chef token returns created item`() {
        val token = chefToken()
        val req = CreateMenuItemRequest(
            date = LocalDate.now(),
            name = "Суп",
            location = "Корпус А",
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
            .andExpect(jsonPath("$.location").value("Корпус А"))
    }

    @Test
    fun `POST menu with student token is forbidden`() {
        val token = studentToken()
        val req = CreateMenuItemRequest(
            date = LocalDate.now(),
            name = "Второе",
            location = "Корпус А",
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
            location = "Корпус А",
            description = "Фруктовый"
        )

        mockMvc.perform(
            post("/api/v1/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        )
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun `POST menu without location returns 400`() {
        val token = chefToken()
        val rawJson = """
            {
              "date":"${LocalDate.now()}",
              "name":"Суп",
              "description":"Гороховый"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/menu")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawJson)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET menu supports location filter`() {
        val token = studentToken()
        val date = LocalDate.now()
        menuRepository.save(
            MenuEntity(
                date = date,
                name = "Суп",
                location = "Корпус А",
                description = "Гороховый",
            )
        )
        menuRepository.save(
            MenuEntity(
                date = date,
                name = "Каша",
                location = "Корпус Б",
                description = "Гречневая",
            )
        )

        mockMvc.perform(
            get("/api/v1/menu")
                .header("Authorization", "Bearer $token")
                .param("date", date.toString())
                .param("location", "Корпус А")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].location").value("Корпус А"))
    }

    @Test
    fun `GET menu locations returns distinct values`() {
        val token = studentToken()
        val date = LocalDate.now()
        menuRepository.save(
            MenuEntity(
                date = date,
                name = "Суп",
                location = "Корпус А",
                description = "Гороховый",
            )
        )
        menuRepository.save(
            MenuEntity(
                date = date,
                name = "Каша",
                location = "Корпус Б",
                description = "Гречневая",
            )
        )

        mockMvc.perform(
            get("/api/v1/menu/locations")
                .header("Authorization", "Bearer $token")
                .param("date", date.toString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }
}
