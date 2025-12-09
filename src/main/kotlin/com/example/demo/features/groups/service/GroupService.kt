package com.example.demo.features.groups.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.groups.dto.CreateGroupRequest
import com.example.demo.features.groups.dto.GroupResponse
import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository
) {

    // 1. Получить все группы (для списка в админке)
    fun getAllGroups(): List<GroupResponse> {
        return groupRepository.findAll().map { group ->
            toGroupResponse(group)
        }
    }

    // 2. Создать группу
    fun createGroup(req: CreateGroupRequest): GroupResponse {
        if (groupRepository.existsByGroupName(req.name)) {
            throw RuntimeException("Группа с таким названием уже существует")
        }
        val group = GroupEntity(groupName = req.name, curator = null)
        val saved = groupRepository.save(group)
        return toGroupResponse(saved)
    }

    // 3. Назначить куратора
    @Transactional
    fun setCurator(groupId: Int, curatorId: UUID): GroupResponse {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw RuntimeException("Группа не найдена")

        val user = userRepository.findByIdOrNull(curatorId)
            ?: throw RuntimeException("Пользователь не найден")

        // Валидация: А точно ли это куратор?
        if (user.roles.any {  it != Role.CURATOR } && user.roles.any { it != Role.ADMIN }) {
            throw RuntimeException("Пользователь не имеет роли CURATOR")
        }

        group.curator = user // Hibernate сам обновит связь
        return toGroupResponse(groupRepository.save(group))
    }

    // 4. Убрать куратора
    @Transactional
    fun removeCurator(groupId: Int): GroupResponse {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw RuntimeException("Группа не найдена")

        group.curator = null
        return toGroupResponse(groupRepository.save(group))
    }

    // 5. Добавить студента в группу
    @Transactional
    fun addStudentToGroup(groupId: Int, studentId: UUID) {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw RuntimeException("Группа не найдена")

        val student = userRepository.findByIdOrNull(studentId)
            ?: throw RuntimeException("Студент не найден")

        if (student.roles.any {it != Role.STUDENT }) {
            throw RuntimeException("Можно добавлять только студентов")
        }

        student.group = group // Меняем поле в UserEntity
        userRepository.save(student)
    }

    // 6. Удалить студента из группы (отвязать)
    @Transactional
    fun removeStudentFromGroup(studentId: UUID) {
        val student = userRepository.findByIdOrNull(studentId)
            ?: throw RuntimeException("Студент не найден")

        student.group = null
        userRepository.save(student)
    }

    // 7. Удалить группу целиком
    @Transactional
    fun deleteGroup(groupId: Int) {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw RuntimeException("Группа не найдена")

        // ВАЖНО: Сначала отвязываем студентов, иначе БД не даст удалить (Foreign Key)
        // Либо студенты удалятся вместе с группой (если CascadeType.ALL), что ПЛОХО.
        // Поэтому лучше вручную отвязать.
        val students = userRepository.findAll().filter { it.group?.id == groupId }
        students.forEach {
            it.group = null
        }
        userRepository.saveAll(students)

        groupRepository.delete(group)
    }

    // Вспомогательный метод маппинга
    private fun toGroupResponse(group: GroupEntity): GroupResponse {
        val count = group.id?.let { userRepository.countByGroup_Id(it) } ?: 0

        return GroupResponse(
            id = group.id ?: 0,
            name = group.groupName,
            curatorId = group.curator?.id,
            // Безопасное извлечение полей (null-safe)
            curatorName = group.curator?.name,
            curatorSurname = group.curator?.surname,
            curatorFatherName = group.curator?.fatherName,
            studentCount = count
        )
    }
}