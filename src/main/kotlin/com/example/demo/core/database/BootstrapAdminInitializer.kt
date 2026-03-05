package com.example.demo.core.database

import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class BootstrapAdminInitializer(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.bootstrap-admin.enabled:true}")
    private val enabled: Boolean,
    @Value("\${app.bootstrap-admin.login:}")
    private val login: String,
    @Value("\${app.bootstrap-admin.password:}")
    private val password: String,
    @Value("\${app.bootstrap-admin.name:Системный}")
    private val name: String,
    @Value("\${app.bootstrap-admin.surname:Администратор}")
    private val surname: String,
    @Value("\${app.bootstrap-admin.father-name:Служебный}")
    private val fatherName: String,
    @Value("\${app.bootstrap-admin.group-name:101}")
    private val groupName: String,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(BootstrapAdminInitializer::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!enabled) return
        val normalizedLogin = login.trim()
        val normalizedPassword = password.trim()
        if (normalizedLogin.isBlank() || normalizedPassword.isBlank()) {
            log.info("Bootstrap admin skipped: BOOTSTRAP_ADMIN_LOGIN/PASSWORD is not configured")
            return
        }

        if (userRepository.existsByLogin(normalizedLogin)) {
            log.info("Bootstrap admin already exists: {}", normalizedLogin)
            return
        }

        val targetGroup = resolveGroup()
        val admin = userRepository.save(
            UserEntity(
                login = normalizedLogin,
                passwordHash = passwordEncoder.encode(normalizedPassword),
                roles = mutableSetOf(
                    Role.ADMIN,
                    Role.REGISTRATOR,
                    Role.CURATOR,
                    Role.CHEF,
                    Role.STUDENT,
                ),
                name = name.trim().ifBlank { "Системный" },
                surname = surname.trim().ifBlank { "Администратор" },
                fatherName = fatherName.trim().ifBlank { "Служебный" },
                group = targetGroup,
                studentCategory = StudentCategory.SVO,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

        if (targetGroup != null) {
            targetGroup.curators.add(admin)
            groupRepository.save(targetGroup)
        }

        log.warn("Bootstrap admin created: login={}, group={}", normalizedLogin, targetGroup?.groupName ?: "-")
    }

    private fun resolveGroup(): GroupEntity? {
        val normalizedGroupName = groupName.trim()
        if (normalizedGroupName.isBlank()) return null
        return groupRepository.findByGroupName(normalizedGroupName)
            ?: groupRepository.save(GroupEntity(groupName = normalizedGroupName))
    }
}
