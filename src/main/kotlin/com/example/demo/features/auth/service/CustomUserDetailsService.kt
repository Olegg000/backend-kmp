package com.example.demo.features.auth.service

import com.example.demo.core.database.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(private val userRepo: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepo.findByLogin(username) ?: throw UsernameNotFoundException("Not found")

        // Превращаем Set<Role> в List<SimpleGrantedAuthority>
        val authorities = user.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }

        return User(user.login, user.passwordHash, authorities)
    }
}