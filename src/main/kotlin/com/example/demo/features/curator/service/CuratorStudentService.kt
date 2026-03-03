package com.example.demo.features.curator.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.auth.dto.AdminUserDto
import com.example.demo.features.curator.dto.CuratorStudentRow
import com.example.demo.features.curator.dto.CuratorStudentCategoryUpdateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CuratorStudentService(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
) {

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
}
