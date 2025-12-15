package com.example.demo.features.auth

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.PasswordResetLogRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.features.auth.dto.Auth
import com.example.demo.features.auth.dto.CreateUserRequest
import com.example.demo.features.auth.dto.RegUser
import com.example.demo.features.auth.service.UserServiceQ
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver::class)
@Transactional
@DisplayName("UserService - Аутентификация и регистрация")
class UserServiceTest {

    @Autowired
    private lateinit var userService: UserServiceQ

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @MockkBean
    private lateinit var authenticationManager: AuthenticationManager

    @Autowired
    private lateinit var jwtUtils: JwtUtils

    @Autowired
    private lateinit var passwordResetLogRepository: PasswordResetLogRepository

    @BeforeEach
    fun setup() {
        // Mock для успешной аутентификации
        every {
            authenticationManager.authenticate(any())
        } returns UsernamePasswordAuthenticationToken("test", "test")
    }

    @Test
    @DisplayName("Успешная регистрация пользователя")
    fun `reg should create user successfully`() {
        // Given
        val regDto = RegUser(
            login = "new-user",
            password = "password123",
            roles = Role.STUDENT,
            name = "Иван",
            surname = "Иванов",
            fatherName = "Иванович"
        )

        // When
        userService.reg(regDto)

        // Then
        val saved = userRepository.findByLogin("new-user")
        assertNotNull(saved)
        assertEquals("Иван", saved?.name)
        assertTrue(passwordEncoder.matches("password123", saved?.passwordHash))
    }

    @Test
    @DisplayName("Регистрация с существующим логином выбрасывает исключение")
    fun `reg should throw exception for duplicate login`() {
        // Given
        userRepository.save(
            UserEntity(
                login = "existing",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Test",
                surname = "User",
                fatherName = "Test"
            )
        )

        val regDto = RegUser(
            login = "existing",
            password = "pass",
            roles = Role.STUDENT,
            name = "Another",
            surname = "User",
            fatherName = "Test"
        )

        // When & Then
        assertThrows(RuntimeException::class.java) {
            userService.reg(regDto)
        }
    }

    @Test
    @DisplayName("Авторизация генерирует JWT токен, ключи и профиль пользователя")
    fun `auth should return token keys and profile`() {
        // Given
        val user = userRepository.save(
            UserEntity(
                login = "test-auth",
                passwordHash = passwordEncoder.encode("password"),
                roles = mutableSetOf(Role.STUDENT),
                name = "Test",
                surname = "User",
                fatherName = "Testovich"
            )
        )

        val authRequest = Auth("test-auth", "password")

        // When
        val response = userService.auth(authRequest)

        // Then
        assertNotNull(response.token)
        assertNotNull(response.privateKey)
        assertNotNull(response.publicKey)
        assertTrue(response.roles.contains("STUDENT"))

        assertEquals(user.id, response.userId)
        assertEquals("test-auth", response.login)
        assertEquals("Test", response.name)
        assertEquals("User", response.surname)
        assertEquals("Testovich", response.fatherName)
        assertNull(response.groupId)
    }

    @Test
    @DisplayName("Авторизация генерирует ключи при первом входе")
    fun `auth should generate keys on first login`() {
        // Given
        val user = userRepository.save(
            UserEntity(
                login = "new-login",
                passwordHash = passwordEncoder.encode("pass"),
                roles = mutableSetOf(Role.STUDENT),
                name = "Test",
                surname = "User",
                fatherName = "Test",
                publicKey = null,
                encryptedPrivateKey = null
            )
        )

        val authRequest = Auth("new-login", "pass")

        // When
        val response = userService.auth(authRequest)

        // Then
        val updated = userRepository.findByLogin("new-login")
        assertNotNull(updated?.publicKey)
        assertNotNull(updated?.encryptedPrivateKey)

        assertEquals(updated!!.id, response.userId)
        assertEquals("new-login", response.login)
    }

    @Test
    @DisplayName("Автоматическое создание пользователя генерирует логин и пароль")
    fun `createUserAuto should generate login and password`() {
        // Given
        val group = groupRepository.save(GroupEntity(groupName = "ПИ-21", curator = null))

        val request = CreateUserRequest(
            roles = mutableSetOf(Role.STUDENT),
            name = "Петр",
            surname = "Петров",
            fatherName = "Петрович",
            groupId = group.id   // студенту обязательна группа
        )

        // When
        val response = userService.createUserAuto(request)

        // Then
        assertNotNull(response.userId)
        assertTrue(response.login.startsWith("st-"), "Логин студента должен начинаться с st-")
        assertNotNull(response.passwordClearText)
        assertTrue(response.passwordClearText.length >= 8, "Пароль должен быть минимум 8 символов")
        assertEquals("Петров Петр", response.fullName)
    }

    @Test
    @DisplayName("Создание студента требует указания группы")
    fun `createUserAuto should require group for students`() {
        // Given
        val request = CreateUserRequest(
            roles = mutableSetOf(Role.STUDENT),
            name = "Test",
            surname = "Test",
            fatherName = "Test",
            groupId = null // Группа не указана!
        )

        // When & Then
        val exception = assertThrows(RuntimeException::class.java) {
            userService.createUserAuto(request)
        }
        assertTrue(exception.message!!.contains("нужна группа"))
    }

    @Test
    @DisplayName("Создание повара использует префикс chef-")
    fun `createUserAuto should use chef prefix for chef role`() {
        // Given
        val request = CreateUserRequest(
            roles = mutableSetOf(Role.CHEF),
            name = "Мария",
            surname = "Поварова",
            fatherName = "Кулинаровна",
            groupId = null
        )

        // When
        val response = userService.createUserAuto(request)

        // Then
        assertTrue(response.login.startsWith("chef-"), "Логин повара должен начинаться с chef-")
    }

    @Test
    @DisplayName("Сброс пароля генерирует новый пароль")
    fun `resetPassword should generate new password`() {
        // Given
        val user = userRepository.save(
            UserEntity(
                login = "reset-test",
                passwordHash = passwordEncoder.encode("old-password"),
                roles = mutableSetOf(Role.STUDENT),
                name = "Test",
                surname = "User",
                fatherName = "Test"
            )
        )

        // When
        val response = userService.resetPassword(user.id!!, "admin")

        // Then
        assertNotNull(response.passwordClearText)
        val updated = userRepository.findById(user.id!!).get()
        assertTrue(
            passwordEncoder.matches(response.passwordClearText, updated.passwordHash),
            "Новый пароль должен быть сохранен"
        )

        val logs = passwordResetLogRepository.findAll()
        assertEquals(1, logs.size)
        assertEquals(user.id, logs[0].user.id)
    }

    @Test
    @DisplayName("Сброс пароля ограничен тремя попытками в сутки")
    fun `resetPassword should be limited to 3 per day`() {
        val user = userRepository.save(
            UserEntity(
                login = "reset-limit",
                passwordHash = passwordEncoder.encode("old"),
                roles = mutableSetOf(Role.STUDENT),
                name = "Test",
                surname = "User",
                fatherName = "Test"
            )
        )

        // 3 успешных сброса
        repeat(3) {
            userService.resetPassword(user.id!!, "admin")
        }

        // 4-й должен упасть
        val ex = assertThrows(RuntimeException::class.java) {
            userService.resetPassword(user.id!!, "admin")
        }
        assertTrue(ex.message!!.contains("Слишком много попыток"), "Должно быть сообщение о лимите")
    }

    @Test
    @DisplayName("Импорт студентов из CSV создает пользователей")
    fun `importStudentsFromCsv should create students`() {
        // Given
        val group = groupRepository.save(GroupEntity(groupName = "ПИ-21", curator = null))

        val csv = """
            Фамилия,Имя,Отчество,Группа
            Иванов,Иван,Иванович,ПИ-21
            Петров,Петр,Петрович,ПИ-21
        """.trimIndent()

        // When
        userService.importStudentsFromCsv(csv)

        // Then
        val students = userRepository.findAllByGroup(group)
        assertEquals(2, students.size, "Должно быть создано 2 студента")

        val ivanov = students.find { it.surname == "Иванов" }
        assertNotNull(ivanov)
        assertEquals("Иван", ivanov?.name)
        assertTrue(ivanov?.login?.startsWith("st-") == true)
    }


}