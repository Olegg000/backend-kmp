package com.example.demo.features.time

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TimeSyncController - текущее время сервера")
class TimeSyncControllerTest(

    @Autowired private val mockMvc: MockMvc
) {

    @Test
    fun `GET current returns timestamp and iso8601`() {
        mockMvc.perform(
            get("/api/v1/time/current")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.iso8601").exists())
    }
}