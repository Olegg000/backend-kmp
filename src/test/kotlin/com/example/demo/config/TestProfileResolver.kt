package com.example.demo.config

import org.springframework.test.context.ActiveProfilesResolver

class TestProfileResolver : ActiveProfilesResolver {
    override fun resolve(testClass: Class<*>): Array<String> {
        val profile = System.getProperty("dbProfile") ?: "test"
        return arrayOf(profile)
    }
}