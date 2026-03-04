package com.example.demo.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.concurrent.atomic.AtomicInteger

class RateLimitFilterTest {

    @Test
    fun `when forwarded ip is disabled header is ignored`() {
        val filter = RateLimitFilter(
            rateLimitEnabled = true,
            useForwardedIp = false,
            loginPerMinute = 1,
            qrPerMinute = 100,
            batchPerMinute = 100,
        )
        val chainCalls = AtomicInteger(0)
        val chain = countingChain(chainCalls)

        val firstResponse = runLoginRequest(
            filter = filter,
            chain = chain,
            remoteAddr = "10.0.0.1",
            forwardedFor = "203.0.113.1",
        )
        val secondResponse = runLoginRequest(
            filter = filter,
            chain = chain,
            remoteAddr = "10.0.0.1",
            forwardedFor = "203.0.113.2",
        )

        assertEquals(200, firstResponse.status)
        assertEquals(429, secondResponse.status)
        assertEquals(1, chainCalls.get())
    }

    @Test
    fun `when forwarded ip is enabled header defines client key`() {
        val filter = RateLimitFilter(
            rateLimitEnabled = true,
            useForwardedIp = true,
            loginPerMinute = 1,
            qrPerMinute = 100,
            batchPerMinute = 100,
        )
        val chainCalls = AtomicInteger(0)
        val chain = countingChain(chainCalls)

        val firstResponse = runLoginRequest(
            filter = filter,
            chain = chain,
            remoteAddr = "10.0.0.1",
            forwardedFor = "203.0.113.1",
        )
        val secondResponse = runLoginRequest(
            filter = filter,
            chain = chain,
            remoteAddr = "10.0.0.1",
            forwardedFor = "203.0.113.2",
        )

        assertEquals(200, firstResponse.status)
        assertEquals(200, secondResponse.status)
        assertEquals(2, chainCalls.get())
    }

    private fun runLoginRequest(
        filter: RateLimitFilter,
        chain: FilterChain,
        remoteAddr: String,
        forwardedFor: String?,
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest("POST", "/api/v1/auth/login").apply {
            this.remoteAddr = remoteAddr
            if (forwardedFor != null) {
                addHeader("X-Forwarded-For", forwardedFor)
            }
        }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        return response
    }

    private fun countingChain(counter: AtomicInteger): FilterChain {
        return FilterChain { _: ServletRequest, _: ServletResponse ->
            counter.incrementAndGet()
        }
    }
}
