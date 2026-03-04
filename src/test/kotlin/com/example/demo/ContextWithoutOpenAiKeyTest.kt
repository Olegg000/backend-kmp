package com.example.demo

import com.example.demo.config.TestProfileResolver
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver::class)
class ContextWithoutOpenAiKeyTest {

    @Test
    fun `context starts without OpenAI key`() {
        // Regression guard: test context must start without SPRING_AI_OPENAI_API_KEY.
    }
}
