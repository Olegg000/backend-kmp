package com.example.demo

import com.example.demo.config.TestProfileResolver
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver::class)
class JpaEntitiesDebugTest {

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun printEntities() {
        val mm = entityManagerFactory.metamodel
        mm.entities.forEach {
            println("JPA entity: ${it.name} -> ${it.javaType.name}")
        }
    }
}
