package com.example.demo.core.database

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class LegacyMealTypeStartupValidator(
    private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        val legacyTxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_transaction WHERE meal_type NOT IN (0, 1)",
            Long::class.java
        ) ?: 0L
        if (legacyTxCount > 0) {
            throw IllegalStateException(
                "Обнаружены старые типы питания в meal_transaction. Поддерживаются только BREAKFAST и LUNCH."
            )
        }

        val legacySuspiciousCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM suspicious_transaction WHERE meal_type NOT IN ('BREAKFAST', 'LUNCH')",
            Long::class.java
        ) ?: 0L
        if (legacySuspiciousCount > 0) {
            throw IllegalStateException(
                "Обнаружены старые типы питания в suspicious_transaction. Поддерживаются только BREAKFAST и LUNCH."
            )
        }
    }
}
