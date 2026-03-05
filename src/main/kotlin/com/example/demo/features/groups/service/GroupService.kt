package com.example.demo.features.groups.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.groups.dto.CuratorSummary
import com.example.demo.features.groups.dto.CreateGroupRequest
import com.example.demo.features.groups.dto.GroupResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository
) {

    // 1. Получить все группы (для списка в админке)
    @Transactional(readOnly = true)
    fun getAllGroups(): List<GroupResponse> {
        return groupRepository.findAllWithCurators().map { group ->
            toGroupResponse(group)
        }
    }

    @Transactional(readOnly = true)
    fun getMyGroups(curatorLogin: String): List<GroupResponse> {
        val curator = userRepository.findByLogin(curatorLogin)
            ?: throw BusinessException(
                code = "CURATOR_NOT_FOUND",
                userMessage = "Куратор не найден",
                status = HttpStatus.NOT_FOUND,
            )

        if (!curator.roles.contains(Role.CURATOR)) {
            throw BusinessException(
                code = "CURATOR_ROLE_REQUIRED",
                userMessage = "Доступно только пользователю с ролью CURATOR",
                status = HttpStatus.FORBIDDEN,
            )
        }

        val curatorId = curator.id ?: throw BusinessException(
            code = "CURATOR_ID_MISSING",
            userMessage = "У куратора отсутствует идентификатор",
            status = HttpStatus.CONFLICT,
        )

        return groupRepository.findAllByCuratorId(curatorId)
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map(::toGroupResponse)
    }

    // 2. Создать группу
    @Transactional
    fun createGroup(req: CreateGroupRequest): GroupResponse {
        val normalizedName = normalizeGroupName(req.name)
        if (normalizedName.isBlank()) {
            throw BusinessException(
                code = "GROUP_NAME_REQUIRED",
                userMessage = "Введите название группы",
                status = HttpStatus.BAD_REQUEST
            )
        }

        val duplicateExists = groupRepository.findAllGroupNames()
            .asSequence()
            .map(::normalizeGroupName)
            .any { it.equals(normalizedName, ignoreCase = true) }

        if (duplicateExists) {
            throw groupAlreadyExistsException()
        }

        return try {
            val group = GroupEntity(groupName = normalizedName)
            val saved = groupRepository.save(group)
            toGroupResponse(saved)
        } catch (_: DataIntegrityViolationException) {
            // Защита от гонки: если параллельно создали такую же группу.
            throw groupAlreadyExistsException()
        }
    }

    // 3. Добавить куратора в группу
    @Transactional
    fun addCurator(groupId: Int, curatorId: UUID): GroupResponse {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw BusinessException(
                code = "GROUP_NOT_FOUND",
                userMessage = "Группа не найдена",
                status = HttpStatus.NOT_FOUND,
            )

        val user = userRepository.findByIdOrNull(curatorId)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )

        // Куратором группы может быть только пользователь с ролью CURATOR.
        if (!user.roles.contains(Role.CURATOR)) {
            throw BusinessException(
                code = "USER_NOT_CURATOR",
                userMessage = "Назначить куратором можно только пользователя с ролью CURATOR.",
                status = HttpStatus.BAD_REQUEST,
            )
        }

        group.curators.add(user)
        return toGroupResponse(groupRepository.save(group))
    }

    // 4. Убрать куратора из группы
    @Transactional
    fun removeCurator(groupId: Int, curatorId: UUID): GroupResponse {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw BusinessException(
                code = "GROUP_NOT_FOUND",
                userMessage = "Группа не найдена",
                status = HttpStatus.NOT_FOUND,
            )

        val user = userRepository.findByIdOrNull(curatorId)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )

        group.curators.remove(user)
        return toGroupResponse(groupRepository.save(group))
    }

    // 5. Добавить студента в группу
    @Transactional
    fun addStudentToGroup(groupId: Int, studentId: UUID) {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw BusinessException(
                code = "GROUP_NOT_FOUND",
                userMessage = "Группа не найдена",
                status = HttpStatus.NOT_FOUND,
            )

        val student = userRepository.findByIdOrNull(studentId)
            ?: throw BusinessException(
                code = "STUDENT_NOT_FOUND",
                userMessage = "Студент не найден",
                status = HttpStatus.NOT_FOUND,
            )

        // Должна быть роль STUDENT (может быть в комбинации с другими)
        if (!student.roles.contains(Role.STUDENT)) {
            throw BusinessException(
                code = "ONLY_STUDENT_CAN_BE_ASSIGNED_TO_GROUP",
                userMessage = "Можно добавлять только студентов",
                status = HttpStatus.BAD_REQUEST,
            )
        }

        student.group = group // Меняем поле в UserEntity
        userRepository.save(student)
    }

    // 6. Удалить студента из группы (отвязать)
    @Transactional
    fun removeStudentFromGroup(studentId: UUID) {
        val student = userRepository.findByIdOrNull(studentId)
            ?: throw BusinessException(
                code = "STUDENT_NOT_FOUND",
                userMessage = "Студент не найден",
                status = HttpStatus.NOT_FOUND,
            )

        student.group = null
        userRepository.save(student)
    }

    // 7. Удалить группу целиком
    @Transactional
    fun deleteGroup(groupId: Int) {
        val group = groupRepository.findByIdOrNull(groupId)
            ?: throw BusinessException(
                code = "GROUP_NOT_FOUND",
                userMessage = "Группа не найдена",
                status = HttpStatus.NOT_FOUND,
            )

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
            curators = group.curators
                .sortedWith(compareBy({ it.surname }, { it.name }, { it.fatherName }))
                .map {
                    CuratorSummary(
                        id = it.id!!,
                        name = it.name,
                        surname = it.surname,
                        fatherName = it.fatherName
                    )
                },
            studentCount = count
        )
    }

    private fun normalizeGroupName(raw: String): String {
        return raw.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun groupAlreadyExistsException(): BusinessException {
        return BusinessException(
            code = "GROUP_ALREADY_EXISTS",
            userMessage = "Группа с таким названием уже существует",
            status = HttpStatus.CONFLICT
        )
    }
}
