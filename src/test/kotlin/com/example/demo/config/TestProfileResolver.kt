package com.example.demo.config

import org.springframework.test.context.ActiveProfilesResolver

class TestProfileResolver : ActiveProfilesResolver {
    override fun resolve(testClass: Class<*>): Array<String> {
        // -DdbProfile=test или it-postgres
        val profile = System.getProperty("dbProfile") ?: "test"
        // val profile = System.getProperty("test") ?: "dbProfile"
        return arrayOf(profile)
    }
}