package com.example.demo.core.logging

import java.util.UUID

fun maskLogin(login: String?): String {
    if (login.isNullOrBlank()) return "unknown"
    if (login.length == 1) return "*"
    if (login.length == 2) return "${login.first()}*"
    return "${login.take(2)}***${login.last()}"
}

fun maskUuid(userId: UUID?): String {
    if (userId == null) return "unknown"
    val raw = userId.toString()
    return "***-${raw.takeLast(8)}"
}
