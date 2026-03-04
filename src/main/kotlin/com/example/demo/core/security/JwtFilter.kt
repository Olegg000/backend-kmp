package com.example.demo.core.security

import com.example.demo.core.security.JwtUtils
import com.example.demo.features.auth.service.CustomUserDetailsService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtFilter(
    private val jwtUtils: JwtUtils,
    private val userDetailsService: CustomUserDetailsService // Напишем ниже
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            if (jwtUtils.validateToken(token)) {
                val login = jwtUtils.getLoginFromToken(token)
                val userDetails = userDetailsService.loadUserByUsername(login)
                if (!userDetails.isEnabled || !userDetails.isAccountNonLocked) {
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = "application/json"
                    response.characterEncoding = "UTF-8"
                    response.writer.write(
                        """{"code":"ACCOUNT_FROZEN_EXPELLED","message":"ACCESS_DENIED","userMessage":"Аккаунт отчисленного пользователя заблокирован","retryable":false,"status":403}"""
                    )
                    return
                }

                val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
