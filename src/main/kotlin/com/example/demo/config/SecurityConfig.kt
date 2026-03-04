package com.example.demo.config

import com.example.demo.core.logging.RequestCorrelationFilter
import com.example.demo.core.security.JwtFilter
import com.example.demo.core.security.RateLimitFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.http.HttpMethod

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val requestCorrelationFilter: RequestCorrelationFilter,
    private val jwtFilter: JwtFilter,
    private val rateLimitFilter: RateLimitFilter,
    @Value("\${security.qr-offline-public:false}")
    private val qrOfflinePublicEnabled: Boolean,
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
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .authorizeHttpRequests { auth ->
                // Разрешаем статику Swagger и API Docs
                auth.requestMatchers(
                    "/v3/api-docs",       // <--- Точное совпадение (важно!)
                    "/v3/api-docs/**",    // <--- Подпапки
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                auth.requestMatchers("/confidence.html").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/auth/login").permitAll()
                auth.requestMatchers("/api/v1/time/current").permitAll() // Синхронизация времени
                if (qrOfflinePublicEnabled) {
                    auth.requestMatchers("/api/v1/qr/validate-offline").permitAll()
                } else {
                    auth.requestMatchers("/api/v1/qr/validate-offline").hasAnyRole("CHEF", "ADMIN")
                }

                auth.requestMatchers("/api/v1/auth/login", "/api/auth/login").permitAll()

                // Роли
                auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                auth.requestMatchers("/api/v1/chef/**").hasAnyRole("CHEF", "ADMIN")
                auth.requestMatchers("/api/v1/curator/**").hasAnyRole("CURATOR", "ADMIN")
                auth.requestMatchers("/api/v1/registrator/**").hasAnyRole("REGISTRATOR", "ADMIN")

                auth.anyRequest().authenticated()
            }
            .addFilterBefore(requestCorrelationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(rateLimitFilter, RequestCorrelationFilter::class.java)
            .addFilterAfter(jwtFilter, RateLimitFilter::class.java)

        return http.build()
    }


    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
