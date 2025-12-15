package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EntityScan(basePackages = ["com.example.demo"])
@EnableJpaRepositories(basePackages = ["com.example.demo"])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}