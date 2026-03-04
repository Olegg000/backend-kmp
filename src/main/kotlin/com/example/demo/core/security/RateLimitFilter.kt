package com.example.demo.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Component
class RateLimitFilter(
    @Value("\${security.rate-limit.enabled:true}")
    private val rateLimitEnabled: Boolean,
    @Value("\${security.rate-limit.use-forwarded-ip:false}")
    private val useForwardedIp: Boolean,
    @Value("\${security.rate-limit.login-per-minute:60}")
    private val loginPerMinute: Int,
    @Value("\${security.rate-limit.qr-per-minute:600}")
    private val qrPerMinute: Int,
    @Value("\${security.rate-limit.batch-per-minute:300}")
    private val batchPerMinute: Int,
) : OncePerRequestFilter() {

    private val windowCounter = ConcurrentHashMap<String, AtomicInteger>()
    private val currentMinuteBucket = AtomicLong(-1L)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!rateLimitEnabled) return true
        return resolveLimit(request) == null
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val limit = resolveLimit(request) ?: run {
            filterChain.doFilter(request, response)
            return
        }

        val minuteBucket = System.currentTimeMillis() / 60_000L
        maybeCleanupWindow(minuteBucket)

        val clientIp = extractClientIp(request)
        val key = "${request.method}:${request.requestURI}:$clientIp:$minuteBucket"
        val count = windowCounter.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()

        if (count > limit) {
            response.status = 429
            response.contentType = "application/json"
            response.writer.write(
                """{"code":"RATE_LIMITED","message":"Too many requests","userMessage":"Слишком много запросов. Повторите позже.","retryable":true,"status":429}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveLimit(request: HttpServletRequest): Int? {
        val path = request.requestURI
        val method = request.method
        return when {
            method == "POST" && (path == "/api/v1/auth/login" || path == "/api/auth/login") -> loginPerMinute
            method == "POST" && (path == "/api/v1/qr/validate" || path == "/api/v1/qr/validate-offline") -> qrPerMinute
            method == "POST" && path == "/api/v1/transactions/batch" -> batchPerMinute
            else -> null
        }
    }

    private fun maybeCleanupWindow(minuteBucket: Long) {
        val previous = currentMinuteBucket.get()
        if (previous == minuteBucket) return
        if (!currentMinuteBucket.compareAndSet(previous, minuteBucket)) return
        if (windowCounter.size < 20_000) return

        val currentSuffix = ":$minuteBucket"
        val previousSuffix = ":${minuteBucket - 1}"
        windowCounter.keys.removeIf { key ->
            !key.endsWith(currentSuffix) && !key.endsWith(previousSuffix)
        }
    }

    private fun extractClientIp(request: HttpServletRequest): String {
        if (useForwardedIp) {
            val forwarded = request.getHeader("X-Forwarded-For")
                ?.substringBefore(',')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (forwarded != null) return forwarded
        }
        return request.remoteAddr ?: "unknown"
    }
}
