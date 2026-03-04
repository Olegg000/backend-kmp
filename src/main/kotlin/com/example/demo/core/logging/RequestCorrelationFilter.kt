package com.example.demo.core.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class RequestCorrelationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(MAX_REQUEST_ID_LENGTH)
            ?: UUID.randomUUID().toString()

        MDC.put(MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
        private const val MAX_REQUEST_ID_LENGTH = 128

        fun currentRequestId(): String? = MDC.get(MDC_KEY)
    }
}
