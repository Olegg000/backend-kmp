package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EntityScan(basePackages = ["com.example.demo"])
@EnableJpaRepositories(basePackages = ["com.example.demo"])
@EnableScheduling
class Application

fun main(args: Array<String>) {
    println("VERSION-FOOD-1.0.0")
    runApplication<Application>(*args)
}