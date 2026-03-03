package com.example.demo.features.groups

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("GroupController - regression for lazy curators loading")
class GroupControllerRegressionTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val jwtUtils: JwtUtils,
) {

    @Test
    @DisplayName("GET /api/v1/groups отдает кураторов без LazyInitializationException")
    fun `get groups should return curator list`() {
        val admin = userRepository.save(
            UserEntity(
                login = "admin-group-regression",
                passwordHash = "h",
                roles = mutableSetOf(Role.ADMIN),
                name = "Админ",
                surname = "Тестов",
                fatherName = "Системович"
            )
        )

        val curator = userRepository.save(
            UserEntity(
                login = "curator-group-regression",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Руководителевна"
            )
        )

        val group = groupRepository.save(GroupEntity(groupName = "ПИ-51"))
        group.curators = mutableSetOf(curator)
        groupRepository.save(group)

        val token = jwtUtils.generateToken(admin.login, admin.roles)

        mockMvc.perform(
            get("/api/v1/groups")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name=='ПИ-51')].studentCount", hasItem(0)))
            .andExpect(jsonPath("$[?(@.name=='ПИ-51')].curators[0].id", hasItem(curator.id.toString())))
    }
}
