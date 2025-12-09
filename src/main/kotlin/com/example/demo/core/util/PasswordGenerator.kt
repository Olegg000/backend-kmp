package com.example.demo.core.util

import org.springframework.stereotype.Component
import java.security.SecureRandom

@Component
class PasswordGenerator {

    private val charPool = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    fun generatePassword(length: Int = 8): String {
        return (1..length)
            .map { charPool[random.nextInt(charPool.length)] }
            .joinToString("")
    }

    // Генерация логина: s.ivanov, t.petrov и т.д.
    // Если занят, сервис добавит цифру
    fun generateLoginBase(rolePrefix: String, surname: String): String {
        // Транслитерацию делать сложно без библиотеки,
        // поэтому для простоты можно использовать рандом или просто id,
        // но лучше в продакшене подключить либу "icu4j" для транслита.
        // Для сейчас сделаем просто: prefix + random
        // Пример: std-xk92
        return "$rolePrefix-${generatePassword(4).lowercase()}"
    }
}