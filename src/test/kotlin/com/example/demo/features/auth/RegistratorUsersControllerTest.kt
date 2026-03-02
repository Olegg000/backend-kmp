package com.example.demo.features.auth

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.features.auth.dto.UpdateUserRolesRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("RegistratorController - управление ролями и пользователями")
class RegistratorUsersControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val objectMapper: ObjectMapper
) {

    private fun createAdminToken(): String {
        val admin = userRepository.save(
            UserEntity(
                login = "admin-reg",
                passwordHash = "hash",
                roles = mutableSetOf(Role.ADMIN),
                name = "Админ",
                surname = "Админов",
                fatherName = "Админович"
            )
        )
        return jwtUtils.generateToken(admin.login, admin.roles)
    }

    @Test
    @DisplayName("PATCH roles обновляет роли пользователя")
    fun `patch roles updates user roles`() {
        val token = createAdminToken()

        val user = userRepository.save(
            UserEntity(
                login = "to-update",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Петров",
                fatherName = "Тестович"
            )
        )

        val request = UpdateUserRolesRequest(setOf(Role.CHEF))

        mockMvc.perform(
            patch("/api/v1/registrator/users/${user.id}/roles")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.login").value("to-update"))
            .andExpect(jsonPath("$.roles.length()").value(1))
    }

    @Test
    @DisplayName("PATCH roles с пустым списком ролей даёт 400")
    fun `patch roles with empty set returns 400`() {
        val token = createAdminToken()

        val user = userRepository.save(
            UserEntity(
                login = "to-update2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Иванов",
                fatherName = "Тестович"
            )
        )

        val request = UpdateUserRolesRequest(emptySet())

        mockMvc.perform(
            patch("/api/v1/registrator/users/${user.id}/roles")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("DELETE удаляет пользователя")
    fun `delete user removes it from database`() {
        val token = createAdminToken()

        val user = userRepository.save(
            UserEntity(
                login = "to-delete",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Удаляемый",
                fatherName = "Тестович"
            )
        )

        mockMvc.perform(
            delete("/api/v1/registrator/users/${user.id}")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)

        val exists = userRepository.findById(user.id!!).isPresent
        assert(!exists)
    }

    @Test
    @DisplayName("DELETE без прав даёт 403")
    fun `delete user forbidden for student`() {
        val student = userRepository.save(
            UserEntity(
                login = "student-reg",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент",
                surname = "Тестовый",
                fatherName = "Учащийся"
            )
        )
        val token = jwtUtils.generateToken(student.login, student.roles)

        val victimId = UUID.randomUUID()

        mockMvc.perform(
            delete("/api/v1/registrator/users/$victimId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("GET /api/v1/registrator/users с фильтром по роли возвращает только нужных")
    fun `get users by role`() {
        val token = createAdminToken()

        val student = userRepository.save(
            UserEntity(
                login = "stud-list",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент",
                surname = "Список",
                fatherName = "Тестович"
            )
        )

        val chef = userRepository.save(
            UserEntity(
                login = "chef-list",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CHEF),
                name = "Повар",
                surname = "Список",
                fatherName = "Кулинарович"
            )
        )

        mockMvc.perform(
            get("/api/v1/registrator/users")
                .header("Authorization", "Bearer $token")
                .param("role", "STUDENT")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].login").value(student.login))
    }
}
