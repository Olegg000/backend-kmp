package com.example.demo.features.groups

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
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
@DisplayName("GroupController - /api/v1/groups/my")
class GroupControllerMyGroupsTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val jwtUtils: JwtUtils,
) {

    @Test
    @DisplayName("CURATOR получает только свои группы")
    fun `curator should get only own groups`() {
        val curator = userRepository.save(
            UserEntity(
                login = "curator-my-groups",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Петровна",
            )
        )
        val otherCurator = userRepository.save(
            UserEntity(
                login = "curator-other-groups",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Олег",
                surname = "Проверкин",
                fatherName = "Иванович",
            )
        )

        val myGroupA = groupRepository.save(GroupEntity(groupName = "ПИ-41"))
        val myGroupB = groupRepository.save(GroupEntity(groupName = "ПИ-42"))
        val otherGroup = groupRepository.save(GroupEntity(groupName = "ПИ-99"))
        myGroupA.curators = mutableSetOf(curator)
        myGroupB.curators = mutableSetOf(curator)
        otherGroup.curators = mutableSetOf(otherCurator)
        groupRepository.save(myGroupA)
        groupRepository.save(myGroupB)
        groupRepository.save(otherGroup)

        val token = jwtUtils.generateToken(curator.login, curator.roles)

        mockMvc.perform(
            get("/api/v1/groups/my")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].name", hasItems("ПИ-41", "ПИ-42")))
            .andExpect(jsonPath("$[*].name", not(hasItem("ПИ-99"))))
    }

    @Test
    @DisplayName("CURATOR без групп получает пустой список")
    fun `curator without groups should get empty list`() {
        val curator = userRepository.save(
            UserEntity(
                login = "curator-without-groups",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Анна",
                surname = "Безгруппова",
                fatherName = "Сергеевна",
            )
        )

        val token = jwtUtils.generateToken(curator.login, curator.roles)

        mockMvc.perform(
            get("/api/v1/groups/my")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    @DisplayName("Пользователь без роли CURATOR получает 403")
    fun `non-curator should get forbidden`() {
        val admin = userRepository.save(
            UserEntity(
                login = "admin-groups-my-forbidden",
                passwordHash = "h",
                roles = mutableSetOf(Role.ADMIN),
                name = "Админ",
                surname = "Тестов",
                fatherName = "Системович",
            )
        )

        val token = jwtUtils.generateToken(admin.login, admin.roles)

        mockMvc.perform(
            get("/api/v1/groups/my")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }
}
