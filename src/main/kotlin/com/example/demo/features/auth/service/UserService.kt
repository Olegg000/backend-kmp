package com.example.demo.features.auth.service

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.core.util.CryptoUtils
import com.example.demo.core.util.PasswordGenerator
import com.example.demo.core.util.TransliterationUtils
import com.example.demo.features.auth.dto.Auth
import com.example.demo.features.auth.dto.AuthReturns
import com.example.demo.features.auth.dto.CreateUserRequest
import com.example.demo.features.auth.dto.RegUser
import com.example.demo.features.auth.dto.RegistrationDto
import com.example.demo.features.auth.dto.UserCredentialsResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserServiceQ(
    private val userRepository: UserRepository,
    private val authenticationManager: AuthenticationManager,
    private val jwtUtils: JwtUtils,
    private val passwordEncoder: PasswordEncoder,
    private val groupRepository: GroupRepository,
) {
    @Autowired
    private lateinit var passwordGenerator: PasswordGenerator

    @Autowired
    private lateinit var transliterationUtils: TransliterationUtils

    fun reg(regDto: RegUser) {
        if (userRepository.findByLogin(regDto.login) != null) {
            throw RuntimeException("Пользователь уже существует")
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
        // 1. Проверка логина и пароля средствами Spring Security
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.login, request.password)
        )

        // 2. Достаем пользователя
        val user = userRepository.findByLogin(request.login)
            ?: throw RuntimeException("User not found") // В реальности не случится, если authenticate прошел

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
            roles = user.roles.map { it.name }, // Превращаем Enum в строки
            privateKey = user.encryptedPrivateKey!!,
            publicKey = user.publicKey!!
        )
    }


    fun registerUser(dto: RegistrationDto) {
        // 1. Валидация: занят ли логин?
        if (userRepository.findByLogin(dto.login) != null) {
            throw RuntimeException("Пользователь с логином ${dto.login} уже существует")
        }

        // 2. Поиск группы (если указана)
        var groupEntity: GroupEntity? = null
        if (dto.groupId != null) {
            groupEntity = groupRepository.findById(dto.groupId)
                .orElseThrow { RuntimeException("Группа не найдена") }
        }

        // 3. Создание сущности
        val user = UserEntity(
            login = dto.login,
            passwordHash = passwordEncoder.encode(dto.password),
            roles = dto.roles.toMutableSet(),
            name = dto.name,
            surname = dto.surname,
            fatherName = dto.fatherName,
            group = groupEntity
        )

        userRepository.save(user)
    }

    @Transactional
    fun createUserAuto(req: CreateUserRequest): UserCredentialsResponse {
        if (req.roles.isEmpty()) throw RuntimeException("Нужна хотя бы одна роль!")

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

        // 3. Проверка группы
        var groupEntity: GroupEntity? = null
        if (req.roles.contains(Role.STUDENT)) {
            if (req.groupId == null) throw RuntimeException("Студенту нужна группа!")
            groupEntity = groupRepository.findByIdOrNull(req.groupId)
                ?: throw RuntimeException("Группа не найдена")
        }

        // 4. Создаем Entity с сетом ролей
        val user = UserEntity(
            login = login,
            passwordHash = passwordEncoder.encode(rawPassword),
            roles = req.roles.toMutableSet(), // Преобразуем в MutableSet
            name = req.name,
            surname = req.surname,
            fatherName = req.fatherName,
            group = groupEntity
        )
        val saved = userRepository.save(user)

        return UserCredentialsResponse(
            userId = saved.id!!,
            login = saved.login,
            passwordClearText = rawPassword,
            fullName = "${saved.surname} ${saved.name}"
        )
    }

    // СБРОС ПАРОЛЯ (Если студент забыл)
    @Transactional
    fun resetPassword(userId: UUID): UserCredentialsResponse {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw RuntimeException("User not found")

        val newPassword = passwordGenerator.generatePassword(8)
        user.passwordHash = passwordEncoder.encode(newPassword)
        // Ключи шифрования при смене пароля лучше не трогать,
        // ИЛИ перешифровывать приватный ключ (это сложная тема).
        // Для MVP: если студент забыл пароль, старые QR перестанут работать,
        // так как он получит новый ключ при логине (см. логику auth).

        userRepository.save(user)

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
                    ?: throw RuntimeException("Группа $groupName не найдена. Создайте её сначала.")

                // Создаем запрос на авто-создание
                val req = CreateUserRequest(
                    roles = mutableSetOf(Role.STUDENT),
                    name = name,
                    surname = surname,
                    fatherName = fatherName,
                    groupId = group.id
                )
                // Вызываем наш метод авто-создания
                createUserAuto(req)
            }
        }
    }

}