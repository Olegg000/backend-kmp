package com.example.demo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class TimeConfig {
    @Bean
    fun businessZoneId(
        @Value("\${app.business-zone:Europe/Samara}") zoneId: String,
    ): ZoneId = ZoneId.of(zoneId)

    @Bean
    fun businessClock(zoneId: ZoneId): Clock = Clock.system(zoneId)
}
