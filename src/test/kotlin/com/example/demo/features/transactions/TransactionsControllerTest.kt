package com.example.demo.features.transactions

import com.example.demo.core.database.MealType
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.security.JwtUtils
import com.example.demo.features.transactions.dto.TransactionSyncItem
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("TransactionsController - REST API batch sync")
class TransactionsControllerTest(

    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
    @Autowired private val permissionRepository: MealPermissionRepository,
    @Autowired private val jwtUtils: JwtUtils,
    @Autowired private val objectMapper: ObjectMapper
) {

    private fun setupChefAndStudent(): Triple<String, UserEntity, UserEntity> {
        val group = groupRepository.save(GroupEntity(groupName = "ИКБО-21", curator = null))

        val curator = userRepository.save(
            UserEntity(
                login = "curator-tr-ctrl",
                passwordHash = "h",
                roles = mutableSetOf(Role.CURATOR),
                name = "Куратор",
                surname = "Группов",
                fatherName = "Руководителевич",