package com.example.demo.features.groups

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.groups.service.GroupService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest
@Import(GroupService::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("GroupService - управление группами и ролями")
class GroupServiceTest(

    @Autowired private val groupService: GroupService,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val userRepository: UserRepository
) {

    private lateinit var group: GroupEntity

    @BeforeEach
    fun setup() {
        group = groupRepository.save(GroupEntity(groupName = "ПИ-21", curator = null))
    }

    @Test
    @DisplayName("Назначение куратора с ролью CURATOR проходит")
    fun `setCurator with CURATOR role should succeed`() {
        val curator = userRepository.save(
            UserEntity(
                login = "cur1",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Иван",
                surname = "Иванов",
                fatherName = "Иванович"
            )
        )

        val resp = groupService.setCurator(group.id!!, curator.id!!)

        assertEquals(curator.id, resp.curatorId)
    }

    @Test
    @DisplayName("Назначение куратора с ролями CURATOR+ADMIN тоже проходит")
    fun `setCurator with CURATOR and ADMIN roles should succeed`() {
        val curator = userRepository.save(
            UserEntity(
                login = "cur2",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR, Role.ADMIN),
                name = "Мария",
                surname = "Классова",
                fatherName = "Руководителевна"
            )
        )

        val resp = groupService.setCurator(group.id!!, curator.id!!)

        assertEquals(curator.id, resp.curatorId)
    }

    @Test
    @DisplayName("Назначение куратора с ролью STUDENT должно падать")
    fun `setCurator with STUDENT role should fail`() {
        val notCurator = userRepository.save(
            UserEntity(
                login = "st1",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент",
                surname = "Петров",
                fatherName = "Петрович"
            )
        )

        val ex = assertThrows(RuntimeException::class.java) {
            groupService.setCurator(group.id!!, notCurator.id!!)
        }
        assertTrue(ex.message!!.contains("не имеет роли CURATOR"))
    }

    @Test
    @DisplayName("Добавление студента с ролью STUDENT в группу")
    fun `addStudentToGroup with STUDENT role should succeed`() {
        val student = userRepository.save(
            UserEntity(
                login = "st2",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент",
                surname = "Сидоров",
                fatherName = "Иванович"
            )
        )

        groupService.addStudentToGroup(group.id!!, student.id!!)

        val updated = userRepository.findById(student.id!!).get()
        assertEquals(group.id, updated.group?.id)
    }

    @Test
    @DisplayName("Добавление не-студента в группу должно падать")
    fun `addStudentToGroup without STUDENT role should fail`() {
        val chef = userRepository.save(
            UserEntity(
                login = "chef1",
                passwordHash = "h",
                roles = mutableSetOf(Role.CHEF),
                name = "Повар",
                surname = "Кулинаров",
                fatherName = "Поварович"
            )
        )

        val ex = assertThrows(RuntimeException::class.java) {
            groupService.addStudentToGroup(group.id!!, chef.id!!)
        }
        assertTrue(ex.message!!.contains("Можно добавлять только студентов"))
    }

    @Test
    @DisplayName("Удаление группы отвязывает студентов")
    fun `deleteGroup should detach students`() {
        val student = userRepository.save(
            UserEntity(
                login = "st3",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Студент",
                surname = "Тестов",
                fatherName = "Учебович",
                group = group
            )
        )

        groupService.deleteGroup(group.id!!)

        val updated = userRepository.findById(student.id!!).get()
        assertNull(updated.group)
    }
}