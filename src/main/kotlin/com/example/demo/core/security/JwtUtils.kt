package com.example.demo.core.security

import com.example.demo.core.database.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtUtils(
    @Value("\${jwt.secret}") private val secretString: String,
    @Value("\${jwt.lifetime}") private val lifetime: Long
) {
    private val secret: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretString.toByteArray())
    }
    private val expirationMs = lifetime.coerceAtLeast(60_000L)

    fun generateToken(login: String, roles: Set<Role>): String {
        return Jwts.builder()
            .setSubject(login)
            .claim("roles", roles.map { it.name })
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(secret, SignatureAlgorithm.HS256)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parserBuilder().setSigningKey(secret).build().parseClaimsJws(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getLoginFromToken(token: String): String {
        return Jwts.parserBuilder().setSigningKey(secret).build()
            .parseClaimsJws(token).body.subject
    }
}
