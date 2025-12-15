package com.example.demo

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver::class)
class MealTransactionSmokeTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val transactionRepository: MealTransactionRepository
) {

    @Test
    fun `save transaction should work`() {
        val user = userRepository.save(
            UserEntity(
                login = "u1",
                passwordHash = "h",
                roles = mutableSetOf(Role.STUDENT),
                name = "Имя",
                surname = "Фамилия",
                fatherName = "Отчество"
            )
        )

        val chef = userRepository.save(
            UserEntity(
                login = "c1",
                passwordHash = "h",
                roles = mutableSetOf(Role.CHEF),
                name = "Имя",
                surname = "Повар",
                fatherName = "Отчество"
            )
        )

        transactionRepository.save(
            MealTransactionEntity(
                transactionHash = "h1",
                timeStamp = LocalDateTime.now(),
                student = user,
                chef = chef,
                isOffline = true,
                mealType = MealType.LUNCH
            )
        )
    }
}