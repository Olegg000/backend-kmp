package com.example.demo.features.auth.service

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.PasswordResetLogEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.PasswordResetLogRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.core.security.JwtUtils
import com.example.demo.core.util.CryptoUtils
import com.example.demo.core.util.PasswordGenerator
import com.example.demo.core.util.TransliterationUtils
import com.example.demo.features.auth.dto.AdminUserDto
import com.example.demo.features.auth.dto.Auth
import com.example.demo.features.auth.dto.AuthReturns
import com.example.demo.features.auth.dto.CreateUserRequest
import com.example.demo.features.auth.dto.RegUser
import com.example.demo.features.auth.dto.RegistrationDto
import com.example.demo.features.auth.dto.UserCredentialsResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
class UserServiceQ(
    private val userRepository: UserRepository,
    private val authenticationManager: AuthenticationManager,
    private val jwtUtils: JwtUtils,
    private val passwordEncoder: PasswordEncoder,
    private val groupRepository: GroupRepository,
    private val passwordResetLogRepository: PasswordResetLogRepository,
    private val businessClock: Clock,
    @Value("\${app.test-mode.enabled:false}")
    private val testModeEnabled: Boolean,
) {
    @Autowired
    private lateinit var passwordGenerator: PasswordGenerator

    @Autowired
    private lateinit var transliterationUtils: TransliterationUtils

    fun reg(regDto: RegUser) {
        if (userRepository.findByLogin(regDto.login) != null) {
            throw BusinessException(
                code = "USER_ALREADY_EXISTS",
                userMessage = "Пользователь уже существует",
                status = HttpStatus.CONFLICT,
            )
        }

        val user = UserEntity(
            login = regDto.login,
            passwordHash = passwordEncoder.encode(regDto.password), // Обязательно хешируем!
            roles = mutableSetOf(regDto.roles),
            name = regDto.name,
            surname = regDto.surname,
            fatherName = regDto.fatherName,
        )
        userRepository.save(user)
    }

    @Transactional
    fun auth(request: Auth): AuthReturns {
        val knownUser = userRepository.findByLogin(request.login)
        if (knownUser != null && knownUser.accountStatus == AccountStatus.FROZEN_EXPELLED) {
            throw BusinessException(
                code = "ACCOUNT_FROZEN_EXPELLED",
                userMessage = "Аккаунт отчисленного пользователя заблокирован. Обратитесь к регистратору.",
                status = HttpStatus.FORBIDDEN,
            )
        }

        // 1. Проверка логина и пароля средствами Spring Security
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.login, request.password)
        )

        // 2. Достаем пользователя
        val user = userRepository.findByLogin(request.login)
            ?: throw BusinessException(
                code = "AUTH_USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.UNAUTHORIZED,
            )

        // 3. ПРОВЕРКА КЛЮЧЕЙ (Фишка твоего проекта)
        // Если пользователь зашел первый раз или у него нет ключей - генерируем
        if (user.publicKey == null || user.encryptedPrivateKey == null) {
            val keys = CryptoUtils.generateKeyPair()
            user.publicKey = keys.first
            // ВАЖНО: В production приватный ключ НЕ должен храниться на сервере!
            // Он должен генерироваться на клиенте и храниться в Keystore/Keychain
            // Здесь мы храним его только для демонстрации/бэкапа
            user.encryptedPrivateKey = keys.second
            userRepository.save(user) // Сохраняем ключи в базу
        }

        // 4. Генерируем токен
        // Можно передать ID юзера сразу в токен, чтобы на фронте было удобно
        val token = jwtUtils.generateToken(user.login, user.roles)

        return AuthReturns(
            token = token,
            roles = user.roles.map { it.name },
            privateKey = user.encryptedPrivateKey!!,
            publicKey = user.publicKey!!,

            userId = user.id!!,
            login = user.login,
            name = user.name,
            surname = user.surname,
            fatherName = user.fatherName,
            groupId = user.group?.id,
            studentCategory = user.studentCategory,
            testMode = testModeEnabled,
        )
    }

    @Transactional
    fun getMyKeys(login: String): com.example.demo.features.auth.dto.AuthKeysDto {
        val user = userRepository.findByLogin(login)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )
            
        // Если ключей почему-то нет (хотя создаются при первом входе), можно генерировать
        if (user.publicKey == null || user.encryptedPrivateKey == null) {
            val keys = CryptoUtils.generateKeyPair()
            user.publicKey = keys.first
            user.encryptedPrivateKey = keys.second
            userRepository.save(user)
        }
        
        return com.example.demo.features.auth.dto.AuthKeysDto(
            publicKey = user.publicKey!!,
            privateKey = user.encryptedPrivateKey!!
        )
    }

    @Transactional(readOnly = true)
    fun getMyProfile(login: String): com.example.demo.features.auth.dto.AuthMeResponse {
        val user = userRepository.findByLogin(login)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )

        val publicKey = user.publicKey ?: throw BusinessException(
            code = "PUBLIC_KEY_MISSING",
            userMessage = "Публичный ключ пользователя отсутствует",
            status = HttpStatus.CONFLICT,
        )
        val privateKey = user.encryptedPrivateKey ?: throw BusinessException(
            code = "PRIVATE_KEY_MISSING",
            userMessage = "Приватный ключ пользователя отсутствует",
            status = HttpStatus.CONFLICT,
        )

        return com.example.demo.features.auth.dto.AuthMeResponse(
            userId = user.id!!,
            roles = user.roles.map { it.name },
            name = user.name,
            surname = user.surname,
            fatherName = user.fatherName,
            groupId = user.group?.id,
            studentCategory = user.studentCategory,
            publicKey = publicKey,
            privateKey = privateKey,
            testMode = testModeEnabled,
        )
    }


    fun registerUser(dto: RegistrationDto) {
        // 1. Валидация: занят ли логин?
        if (userRepository.findByLogin(dto.login) != null) {
            throw BusinessException(
                code = "USER_ALREADY_EXISTS",
                userMessage = "Пользователь с логином ${dto.login} уже существует",
                status = HttpStatus.CONFLICT,
            )
        }

        // 2. Поиск группы (если указана)
        var groupEntity: GroupEntity? = null
        if (dto.groupId != null) {
            groupEntity = groupRepository.findById(dto.groupId)
                .orElseThrow {
                    BusinessException(
                        code = "GROUP_NOT_FOUND",
                        userMessage = "Группа не найдена",
                        status = HttpStatus.NOT_FOUND,
                    )
                }
        }
        validateStudentFields(dto.roles, dto.studentCategory)

        // 3. Создание сущности
        val user = UserEntity(
            login = dto.login,
            passwordHash = passwordEncoder.encode(dto.password),
            roles = dto.roles.toMutableSet(),
            name = dto.name,
            surname = dto.surname,
            fatherName = dto.fatherName,
            group = groupEntity,
            studentCategory = if (dto.roles.contains(Role.STUDENT)) dto.studentCategory else null
        )

        userRepository.save(user)
    }

    @Transactional
    fun createUserAuto(req: CreateUserRequest): UserCredentialsResponse {
        if (req.roles.isEmpty()) {
            throw BusinessException(
                code = "ROLES_REQUIRED",
                userMessage = "Нужна хотя бы одна роль",
            )
        }

        // 1. Определяем префикс логина по приоритету
        val loginPrefix = when {
            req.roles.contains(Role.STUDENT) -> "st"
            req.roles.contains(Role.CURATOR) -> "tchr"
            req.roles.contains(Role.CHEF) -> "chef"
            req.roles.contains(Role.REGISTRATOR) -> "reg"
            else -> "adm"
        }

        // 2. Генерация логина и пароля (как раньше)
        val rawPassword = passwordGenerator.generatePassword(8)
        val surnameSlug = transliterationUtils.transliterate(req.surname)
        val login = "$loginPrefix-$surnameSlug-${passwordGenerator.generatePassword(3).lowercase()}"

        // 3. Проверка группы (если указана)
        var groupEntity: GroupEntity? = null
        if (req.groupId != null) {
            groupEntity = groupRepository.findByIdOrNull(req.groupId)
                ?: throw BusinessException(
                    code = "GROUP_NOT_FOUND",
                    userMessage = "Группа не найдена",
                    status = HttpStatus.NOT_FOUND,
                )
        }
        validateStudentFields(req.roles, req.studentCategory)

        // 4. Создаем Entity с сетом ролей
        val user = UserEntity(
            login = login,
            passwordHash = passwordEncoder.encode(rawPassword),
            roles = req.roles.toMutableSet(), // Преобразуем в MutableSet
            name = req.name,
            surname = req.surname,
            fatherName = req.fatherName,
            group = groupEntity,
            studentCategory = if (req.roles.contains(Role.STUDENT)) req.studentCategory else null
        )
        val saved = userRepository.save(user)

        return UserCredentialsResponse(
            userId = saved.id!!,
            login = saved.login,
            passwordClearText = rawPassword,
            fullName = "${saved.surname} ${saved.name}"
        )
    }

    @Transactional
    fun resetPassword(userId: UUID, requestedByLogin: String? = null): UserCredentialsResponse {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )

        val now = LocalDateTime.now(businessClock)
        val start = now.minusDays(1)

        val resetCount = passwordResetLogRepository.countByUserAndTimestampBetween(user, start, now)
        if (resetCount >= 3) {
            throw BusinessException(
                code = "PASSWORD_RESET_LIMIT_EXCEEDED",
                userMessage = "Слишком много попыток сброса пароля за последние 24 часа",
                status = HttpStatus.TOO_MANY_REQUESTS,
                retryable = true,
            )
        }

        val newPassword = passwordGenerator.generatePassword(8)
        user.passwordHash = passwordEncoder.encode(newPassword)
        userRepository.save(user)

        val resetByUser = requestedByLogin?.let { login ->
            userRepository.findByLogin(login)
        }

        passwordResetLogRepository.save(
            PasswordResetLogEntity(
                user = user,
                resetBy = resetByUser,
                timestamp = now
            )
        )

        return UserCredentialsResponse(
            userId = user.id!!,
            login = user.login,
            passwordClearText = newPassword,
            fullName = "${user.surname} ${user.name}"
        )
    }

    @Transactional
    fun importStudentsFromCsv(csvContent: String) {
        // Простой парсинг: строка за строкой
        val lines = csvContent.lines()

        lines.drop(1).forEach { line -> // Пропускаем заголовок
            if (line.isBlank()) return@forEach

            // Формат CSV: Фамилия,Имя,Отчество,НазваниеГруппы
            val parts = line.split(",")
            if (parts.size >= 4) {
                val surname = parts[0].trim()
                val name = parts[1].trim()
                val fatherName = parts[2].trim()
                val groupName = parts[3].trim()

                // Ищем или создаем группу (если политика позволяет)
                val group = groupRepository.findByGroupName(groupName)
                    ?: throw BusinessException(
                        code = "GROUP_NOT_FOUND",
                        userMessage = "Группа $groupName не найдена. Создайте её сначала.",
                        status = HttpStatus.NOT_FOUND,
                    )

                // Создаем запрос на авто-создание
                val req = CreateUserRequest(
                    roles = mutableSetOf(Role.STUDENT),
                    name = name,
                    surname = surname,
                    fatherName = fatherName,
                    groupId = group.id,
                    studentCategory = StudentCategory.MANY_CHILDREN
                )
                // Вызываем наш метод авто-создания
                createUserAuto(req)
            }
        }
    }

    @Transactional
    fun updateUserRoles(
        userId: UUID,
        newRoles: Set<Role>,
        groupId: Int? = null,
        studentCategory: StudentCategory? = null,
    ): AdminUserDto {
        if (newRoles.isEmpty()) {
            throw BusinessException(
                code = "ROLES_REQUIRED",
                userMessage = "Пользователь должен иметь хотя бы одну роль",
            )
        }

        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
            )

        val hadStudentRole = user.roles.contains(Role.STUDENT)
        val hasStudentRole = newRoles.contains(Role.STUDENT)
        user.roles = newRoles.toMutableSet()
        if (!hasStudentRole) {
            user.studentCategory = null
            user.group = null
        } else {
            if (groupId != null) {
                user.group = groupRepository.findByIdOrNull(groupId)
                    ?: throw BusinessException(
                        code = "GROUP_NOT_FOUND",
                        userMessage = "Группа не найдена",
                    )
            } else if (!hadStudentRole) {
                throw BusinessException(
                    code = "STUDENT_GROUP_REQUIRED",
                    userMessage = "Для роли STUDENT нужно выбрать группу",
                )
            }
            if (!hadStudentRole && studentCategory == null) {
                throw BusinessException(
                    code = "STUDENT_CATEGORY_REQUIRED",
                    userMessage = "Для роли STUDENT нужно выбрать категорию",
                )
            }
            if (studentCategory != null) {
                user.studentCategory = studentCategory
            }
        }
        val saved = userRepository.save(user)

        return AdminUserDto(
            userId = saved.id!!,
            login = saved.login,
            roles = saved.roles,
            name = saved.name,
            surname = saved.surname,
            fatherName = saved.fatherName,
            groupId = saved.group?.id,
            studentCategory = saved.studentCategory,
            accountStatus = saved.accountStatus,
        )
    }

    @Transactional
    fun updateStudentCategory(userId: UUID, category: StudentCategory): AdminUserDto {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
            )
        if (!user.roles.contains(Role.STUDENT)) {
            throw BusinessException(
                code = "CATEGORY_ONLY_FOR_STUDENT",
                userMessage = "Категорию можно менять только студенту",
            )
        }
        user.studentCategory = category
        val saved = userRepository.save(user)
        return AdminUserDto(
            userId = saved.id!!,
            login = saved.login,
            roles = saved.roles,
            name = saved.name,
            surname = saved.surname,
            fatherName = saved.fatherName,
            groupId = saved.group?.id,
            studentCategory = saved.studentCategory,
            accountStatus = saved.accountStatus,
        )
    }

    @Transactional
    fun deleteUser(userId: UUID, currentLogin: String) {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )

        if (user.login == currentLogin) {
            throw BusinessException(
                code = "SELF_DELETE_FORBIDDEN",
                userMessage = "Нельзя удалить самого себя",
                status = HttpStatus.FORBIDDEN,
            )
        }

        userRepository.delete(user)
    }

    fun listUsers(role: Role?, groupId: Int?, search: String?): List<AdminUserDto> {
        var users = userRepository.findAll()

        if (role != null) {
            users = users.filter { it.roles.contains(role) }
        }

        if (groupId != null) {
            users = users.filter { it.group?.id == groupId }
        }

        if (!search.isNullOrBlank()) {
            val q = search.trim().lowercase()
            users = users.filter {
                it.login.lowercase().contains(q) ||
                        it.name.lowercase().contains(q) ||
                        it.surname.lowercase().contains(q) ||
                        it.fatherName.lowercase().contains(q)
            }
        }

        return users.map {
            AdminUserDto(
                userId = it.id!!,
                login = it.login,
                roles = it.roles.toSet(),
                name = it.name,
                surname = it.surname,
                fatherName = it.fatherName,
                groupId = it.group?.id,
                studentCategory = it.studentCategory,
                accountStatus = it.accountStatus,
            )
        }
    }

    @Transactional
    fun updateUserLifecycle(
        userId: UUID,
        status: AccountStatus,
        expelNote: String?,
        requestedByLogin: String,
    ): AdminUserDto {
        val actor = userRepository.findByLogin(requestedByLogin)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )
        if (user.login == requestedByLogin && status == AccountStatus.FROZEN_EXPELLED) {
            throw BusinessException(
                code = "SELF_LIFECYCLE_CHANGE_FORBIDDEN",
                userMessage = "Нельзя переводить самого себя в отчисленные.",
                status = HttpStatus.FORBIDDEN,
            )
        }
        if (status == AccountStatus.FROZEN_EXPELLED && actor.roles.contains(Role.REGISTRATOR)) {
            throw BusinessException(
                code = "REGISTRATOR_EXPEL_FORBIDDEN",
                userMessage = "Регистратор не может отчислять. Используйте табель куратора.",
                status = HttpStatus.FORBIDDEN,
            )
        }

        user.accountStatus = status
        if (status == AccountStatus.FROZEN_EXPELLED) {
            user.expelledAt = LocalDateTime.now(businessClock)
            user.expelledBy = actor
            user.expelNote = expelNote?.trim()?.ifBlank { null }
        } else {
            user.expelledAt = null
            user.expelledBy = null
            user.expelNote = null
        }

        val saved = userRepository.save(user)
        return AdminUserDto(
            userId = saved.id!!,
            login = saved.login,
            roles = saved.roles.toSet(),
            name = saved.name,
            surname = saved.surname,
            fatherName = saved.fatherName,
            groupId = saved.group?.id,
            studentCategory = saved.studentCategory,
            accountStatus = saved.accountStatus,
        )
    }

    private fun validateStudentFields(
        roles: Set<Role>,
        category: StudentCategory?
    ) {
        if (roles.contains(Role.STUDENT)) return
        if (category != null) {
            throw BusinessException(
                code = "CATEGORY_ONLY_FOR_STUDENT",
                userMessage = "Категорию можно задавать только студенту",
            )
        }
    }

}
