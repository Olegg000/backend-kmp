package com.example.demo.features.transactions

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.features.transactions.dto.TransactionSyncItem
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("TransactionsController - REST API batch sync")
class TransactionsControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val permissionRepository: MealPermissionRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val objectMapper: ObjectMapper
) {

    /**
     * Создаём группу, куратора, студента с разрешением на обед сегодня и повара.
     * Возвращаем токен повара и студента.
     */
    private fun setupChefAndStudent(): Triple<String, String, UserEntity> {
        val group = groupRepository.save(GroupEntity(groupName = "ИКБО-21"))

        val curator = userRepository.save(
            UserEntity(
                login = "curator-tr-ctrl",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Куратор",
                surname = "Группов",
                fatherName = "Руководителевич",
                group = group
            )
        )
        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        val student = userRepository.save(
            UserEntity(
                login = "student-tr-ctrl",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
                group = group
            )
        )

        val chef = userRepository.save(
            UserEntity(
                login = "chef-tr-ctrl",
                passwordHash = "h",
                roles = mutableSetOf(Role.CHEF),
                name = "Мария",
                surname = "Поварова",
                fatherName = "Кулинаровна"
            )
        )

        // Разрешение на обед сегодня
        permissionRepository.save(
            MealPermissionEntity(
                date = LocalDate.now(),
                student = student,
                assignedBy = curator,
                reason = "Тест",
                isBreakfastAllowed = false,
                isLunchAllowed = true,
            )
        )

        val chefToken = jwtUtils.generateToken(chef.login, chef.roles)
        val studentToken = jwtUtils.generateToken(student.login, student.roles)

        return Triple(chefToken, studentToken, student)
    }

    @Test
    @DisplayName("POST batch без токена → 403 (доступ запрещен)")
    fun `batch without token should be forbidden`() {
        val (_, _, student) = setupChefAndStudent()

        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.LUNCH,
                transactionHash = "tx-no-token"
            )
        )

        mockMvc.perform(
            post("/api/v1/transactions/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(items))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("POST batch с токеном повара → 200 и successCount как ожидается")
    fun `batch with chef token should sync successfully`() {
        val (chefToken, _, student) = setupChefAndStudent()

        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.LUNCH,
                transactionHash = "tx-ok-1"
            )
        )

        mockMvc.perform(
            post("/api/v1/transactions/batch")
                .header("Authorization", "Bearer $chefToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(items))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.errors").isArray)
    }

    @Test
    @DisplayName("POST batch с токеном студента → 403")
    fun `batch with student token should be forbidden`() {
        val (_, studentToken, student) = setupChefAndStudent()

        val items = listOf(
            TransactionSyncItem(
                studentId = student.id!!,
                timestamp = LocalDateTime.now(),
                mealType = MealType.LUNCH,
                transactionHash = "tx-student"
            )
        )

        mockMvc.perform(
            post("/api/v1/transactions/batch")
                .header("Authorization", "Bearer $studentToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(items))
        )
            .andExpect(status().isForbidden)
    }
}