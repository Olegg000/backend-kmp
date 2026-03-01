package com.example.demo.config

import com.example.demo.core.security.JwtFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtFilter: JwtFilter
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource {
                CorsConfiguration().apply {
                    allowedOrigins = listOf("*")
                    allowedMethods = listOf("*")
                    allowedHeaders = listOf("*")
                }
            }}
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // Разрешаем статику Swagger и API Docs
                auth.requestMatchers(
                    "/v3/api-docs",       // <--- Точное совпадение (важно!)
                    "/v3/api-docs/**",    // <--- Подпапки
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                auth.requestMatchers("/confidence.html").permitAll()
                auth.requestMatchers("/api/v1/auth/**", "/api/auth/**").permitAll()
                auth.requestMatchers("/api/v1/time/current").permitAll() // Синхронизация времени
                auth.requestMatchers("/api/v1/qr/validate-offline").permitAll() // Оффлайн валидация QR

                // Твои контроллеры
                auth.requestMatchers("/api/v1/auth/**", "/api/auth/**").permitAll()

                // Роли
                auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                auth.requestMatchers("/api/v1/chef/**").hasAnyRole("CHEF", "ADMIN")
                auth.requestMatchers("/api/v1/curator/**").hasAnyRole("CURATOR", "ADMIN")
                auth.requestMatchers("/api/v1/registrator/**").hasAnyRole("REGISTRATOR", "ADMIN")

                auth.anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }


    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
