package com.example.demo.features.curator.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.util.PasswordGenerator
import com.example.demo.core.util.TransliterationUtils
import com.example.demo.features.auth.dto.AdminUserDto
import com.example.demo.features.auth.dto.UserCredentialsResponse
import com.example.demo.features.curator.dto.CuratorCreateStudentRequest
import com.example.demo.features.curator.dto.CuratorStudentRow
import com.example.demo.features.curator.dto.CuratorStudentCategoryUpdateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CuratorStudentService(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val passwordGenerator: PasswordGenerator,
    private val transliterationUtils: TransliterationUtils,
    private val passwordEncoder: org.springframework.security.crypto.password.PasswordEncoder
) {

    @Transactional
    fun createStudent(curatorLogin: String, request: CuratorCreateStudentRequest): UserCredentialsResponse {
        val curator = requireCurator(curatorLogin)
        val curatorId = curator.id ?: throw RuntimeException("Куратор не имеет идентификатор")
        if (!groupRepository.existsByIdAndCuratorId(request.groupId, curatorId)) {
            throw RuntimeException("Можно добавлять студентов только в свои группы")
        }
        val group = groupRepository.findById(request.groupId).orElseThrow {
            RuntimeException("Группа не найдена")
        }

        val rawPassword = passwordGenerator.generatePassword(8)
        val login = generateUniqueStudentLogin(request.surname)

        val entity = userRepository.save(
            UserEntity(
                login = login,
                passwordHash = passwordEncoder.encode(rawPassword),
                roles = mutableSetOf(Role.STUDENT),
                name = request.name,
                surname = request.surname,
                fatherName = request.fatherName,
                group = group,
                studentCategory = request.studentCategory
            )
        )

        return UserCredentialsResponse(
            userId = entity.id!!,
            login = entity.login,
            passwordClearText = rawPassword,
            fullName = "${entity.surname} ${entity.name}"
        )
    }

    @Transactional
    fun updateStudentCategory(
        curatorLogin: String,
        studentId: UUID,
        request: CuratorStudentCategoryUpdateRequest
    ): AdminUserDto {
        val curator = requireCurator(curatorLogin)
        val curatorId = curator.id ?: throw RuntimeException("Куратор не имеет идентификатор")
        val student = userRepository.findById(studentId).orElseThrow { RuntimeException("Студент не найден") }
        if (!student.roles.contains(Role.STUDENT)) throw RuntimeException("Пользователь не является студентом")
        val groupId = student.group?.id ?: throw RuntimeException("Студент не привязан к группе")
        if (!groupRepository.existsByIdAndCuratorId(groupId, curatorId)) {
            throw RuntimeException("Можно менять категорию только студентов своих групп")
        }

        student.studentCategory = request.studentCategory
        val saved = userRepository.save(student)
        return AdminUserDto(
            userId = saved.id!!,
            login = saved.login,
            roles = saved.roles,
            name = saved.name,
            surname = saved.surname,
            fatherName = saved.fatherName,
            groupId = saved.group?.id,
            studentCategory = saved.studentCategory
        )
    }

    fun listMyStudents(curatorLogin: String, groupId: Int? = null): List<CuratorStudentRow> {
        val curator = requireCurator(curatorLogin)
        val curatorId = curator.id ?: throw RuntimeException("Куратор не имеет идентификатор")
        val groups = groupRepository.findAllByCuratorId(curatorId)
        val groupsById = groups.associateBy { it.id }
        val allowedGroupIds = groupsById.keys.filterNotNull().toSet()
        if (allowedGroupIds.isEmpty()) return emptyList()
        if (groupId != null && groupId !in allowedGroupIds) {
            throw RuntimeException("Группа недоступна куратору")
        }
        val targetGroupIds = if (groupId == null) allowedGroupIds else setOf(groupId)

        return userRepository.findAll()
            .asSequence()
            .filter { it.roles.contains(Role.STUDENT) }
            .filter { it.group?.id in targetGroupIds }
            .map {
                CuratorStudentRow(
                    userId = it.id!!,
                    fullName = "${it.surname} ${it.name} ${it.fatherName}",
                    groupId = it.group!!.id!!,
                    groupName = it.group!!.groupName,
                    studentCategory = it.studentCategory
                )
            }
            .sortedWith(compareBy({ it.groupName }, { it.fullName }))
            .toList()
    }

    private fun requireCurator(curatorLogin: String): UserEntity {
        val curator = userRepository.findByLogin(curatorLogin) ?: throw RuntimeException("Куратор не найден")
        if (!curator.roles.contains(Role.CURATOR)) {
            throw RuntimeException("Действие доступно только куратору")
        }
        return curator
    }

    private fun generateUniqueStudentLogin(surname: String): String {
        val base = transliterationUtils.transliterate(surname).ifBlank { "student" }
        while (true) {
            val candidate = "st-$base-${passwordGenerator.generatePassword(3).lowercase()}"
            if (!userRepository.existsByLogin(candidate)) return candidate
        }
    }
}
