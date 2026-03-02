package com.example.demo.features.student

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import org.junit.jupiter.api.DisplayName
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
import java.time.DayOfWeek
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("StudentController - личный табель и питание")
class StudentControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val permissionRepository: MealPermissionRepository,
    @Autowired private val jwtUtils: JwtUtils
) {

    private fun createStudentWithPermissions(): Pair<String, UserEntity> {
        val group = groupRepository.save(GroupEntity(groupName = "ПИ-21"))

        val student = userRepository.save(
            UserEntity(
                login = "student-self",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Иван",
                surname = "Студентов",
                fatherName = "Учащийся",
                group = group
            )
        )

        val today = LocalDate.now()
        permissionRepository.save(
            MealPermissionEntity(
                date = today,
                student = student,
                assignedBy = student, // для теста достаточно любого юзера
                reason = "Тест",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
            )
        )

        val token = jwtUtils.generateToken(student.login, student.roles)
        return token to student
    }

    @Test
    @DisplayName("GET /api/v1/student/meals/today возвращает права на сегодня")
    fun `get today meals returns permissions`() {
        val (token, _) = createStudentWithPermissions()

        mockMvc.perform(
            get("/api/v1/student/meals/today")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isBreakfastAllowed").value(true))
            .andExpect(jsonPath("$.isLunchAllowed").value(true))
    }

    @Test
    @DisplayName("GET /api/v1/student/roster возвращает 5 дней недели")
    fun `get roster returns week days`() {
        val (token, student) = createStudentWithPermissions()
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)

        mockMvc.perform(
            get("/api/v1/student/roster")
                .header("Authorization", "Bearer $token")
                .param("startDate", monday.toString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.studentId").value(student.id.toString()))
            .andExpect(jsonPath("$.days.length()").value(5))
    }

    @Test
    @DisplayName("Запрос без токена получает 403 (нет доступа)")
    fun `student endpoints require auth`() {
        mockMvc.perform(
            get("/api/v1/student/meals/today")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }
}
