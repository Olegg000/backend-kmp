package com.example.demo.core.database

import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.MenuEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.MealPermissionRepository
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.core.database.repository.MenuRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class TestModeDemoDataInitializer(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val mealPermissionRepository: MealPermissionRepository,
    private val mealTransactionRepository: MealTransactionRepository,
    private val menuRepository: MenuRepository,
    private val passwordEncoder: PasswordEncoder,
    private val rosterWeekPolicy: RosterWeekPolicy,
    @Value("\${app.test-mode.enabled:false}")
    private val testModeEnabled: Boolean,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(TestModeDemoDataInitializer::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!testModeEnabled) {
            log.info("Test-mode demo seed skipped: app.test-mode.enabled=false")
            return
        }

        val group101 = ensureGroup("Group-101")
        val group102 = ensureGroup("Group-102")
        val groupsByName = mapOf(
            group101.groupName to group101,
            group102.groupName to group102,
        )

        val admin = upsertUser(
            spec = DemoUserSpec(
                login = LOGIN_ADMIN,
                roles = setOf(Role.ADMIN, Role.REGISTRATOR, Role.CHEF, Role.CURATOR, Role.STUDENT),
                name = "Алексей",
                surname = "Админов",
                fatherName = "Иванович",
                groupName = null,
                studentCategory = StudentCategory.SVO,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val chef = upsertUser(
            spec = DemoUserSpec(
                login = LOGIN_CHEF,
                roles = setOf(Role.CHEF),
                name = "Виктор",
                surname = "Баринов",
                fatherName = "Петрович",
                groupName = null,
                studentCategory = null,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val registrator = upsertUser(
            spec = DemoUserSpec(
                login = LOGIN_REGISTRATOR,
                roles = setOf(Role.REGISTRATOR),
                name = "Анна",
                surname = "Учетова",
                fatherName = "Сергеевна",
                groupName = null,
                studentCategory = null,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val curator = upsertUser(
            spec = DemoUserSpec(
                login = LOGIN_CURATOR_101,
                roles = setOf(Role.CURATOR),
                name = "Мария",
                surname = "Классова",
                fatherName = "Владимировна",
                groupName = null,
                studentCategory = null,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val student1011 = upsertUser(
            spec = DemoUserSpec(
                login = "stud_Group-101_1",
                roles = setOf(Role.STUDENT),
                name = "Иван",
                surname = "Первый",
                fatherName = "Александрович",
                groupName = "Group-101",
                studentCategory = StudentCategory.MANY_CHILDREN,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val student1012 = upsertUser(
            spec = DemoUserSpec(
                login = "stud_Group-101_2",
                roles = setOf(Role.STUDENT),
                name = "Дмитрий",
                surname = "Соколов",
                fatherName = "Павлович",
                groupName = "Group-101",
                studentCategory = StudentCategory.SVO,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val student1013 = upsertUser(
            spec = DemoUserSpec(
                login = "stud_Group-101_3",
                roles = setOf(Role.STUDENT),
                name = "Олег",
                surname = "Иванов",
                fatherName = "Сергеевич",
                groupName = "Group-101",
                studentCategory = StudentCategory.SVO,
                accountStatus = AccountStatus.FROZEN_EXPELLED,
                expelNote = "Отчислен в тестовом сценарии",
            ),
            groupsByName = groupsByName,
            expelledBy = curator,
        )
        val student1014 = upsertUser(
            spec = DemoUserSpec(
                login = "stud_Group-101_4",
                roles = setOf(Role.STUDENT),
                name = "Никита",
                surname = "Петров",
                fatherName = "Андреевич",
                groupName = "Group-101",
                studentCategory = StudentCategory.MANY_CHILDREN,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val student1015 = upsertUser(
            spec = DemoUserSpec(
                login = "stud_Group-101_5",
                roles = setOf(Role.STUDENT),
                name = "Егор",
                surname = "Кузнецов",
                fatherName = "Олегович",
                groupName = "Group-101",
                studentCategory = StudentCategory.SVO,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val student1021 = upsertUser(
            spec = DemoUserSpec(
                login = "stud_Group-102_1",
                roles = setOf(Role.STUDENT),
                name = "Артем",
                surname = "Орлов",
                fatherName = "Игоревич",
                groupName = "Group-102",
                studentCategory = StudentCategory.SVO,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val student1022 = upsertUser(
            spec = DemoUserSpec(
                login = "stud_Group-102_2",
                roles = setOf(Role.STUDENT),
                name = "Максим",
                surname = "Смирнов",
                fatherName = "Романович",
                groupName = "Group-102",
                studentCategory = StudentCategory.MANY_CHILDREN,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )
        val studentTest = upsertUser(
            spec = DemoUserSpec(
                login = LOGIN_STUDENT_TEST,
                roles = setOf(Role.STUDENT),
                name = "Тест",
                surname = "Студент",
                fatherName = "Тестович",
                groupName = "Group-101",
                studentCategory = StudentCategory.SVO,
                accountStatus = AccountStatus.ACTIVE,
            ),
            groupsByName = groupsByName,
        )

        val curatorId = curator.id
        if (curatorId != null && group101.curators.none { it.id == curatorId }) {
            group101.curators.add(curator)
            groupRepository.save(group101)
        }

        val currentWeekStart = rosterWeekPolicy.weekStart(rosterWeekPolicy.today())
        val previousWeekStart = currentWeekStart.minusWeeks(1)
        val seedDates = (rosterWeekPolicy.weekDates(previousWeekStart) + rosterWeekPolicy.weekDates(currentWeekStart))
            .distinct()
            .sorted()

        seedMenu(seedDates)
        seedPermissions(
            seedDates = seedDates,
            curator = curator,
            student1011 = student1011,
            student1012 = student1012,
            student1013 = student1013,
            student1014 = student1014,
            student1015 = student1015,
            student1021 = student1021,
            student1022 = student1022,
            studentTest = studentTest,
        )
        ensureTodayPermission(
            student = admin,
            assignedBy = admin,
            reason = "Автовыдача талонов администратору в test-mode",
        )
        ensureTodayPermission(
            student = studentTest,
            assignedBy = curator,
            reason = "Тестовый студент для входа: завтрак и обед",
        )
        seedTransactions(
            seedDates = seedDates,
            chef = chef,
            studentWithFacts = student1011,
        )

        log.warn(
            "Test-mode demo seed applied: users={}, groups=[{}, {}], dateRange={}..{}, accounts=[{}, {}, {}, {}, {}]",
            userRepository.count(),
            group101.groupName,
            group102.groupName,
            seedDates.firstOrNull(),
            seedDates.lastOrNull(),
            admin.login,
            chef.login,
            registrator.login,
            curator.login,
            studentTest.login,
        )
    }

    private fun ensureGroup(groupName: String): GroupEntity =
        groupRepository.findByGroupName(groupName) ?: groupRepository.save(GroupEntity(groupName = groupName))

    private fun upsertUser(
        spec: DemoUserSpec,
        groupsByName: Map<String, GroupEntity>,
        expelledBy: UserEntity? = null,
    ): UserEntity {
        val existing = userRepository.findByLogin(spec.login)
        val user = existing ?: UserEntity(
            login = spec.login,
            passwordHash = passwordEncoder.encode(DEMO_PASSWORD),
            roles = spec.roles.toMutableSet(),
            name = spec.name,
            surname = spec.surname,
            fatherName = spec.fatherName,
        )

        user.passwordHash = passwordEncoder.encode(DEMO_PASSWORD)
        user.roles = spec.roles.toMutableSet()
        user.name = spec.name
        user.surname = spec.surname
        user.fatherName = spec.fatherName
        user.group = spec.groupName?.let(groupsByName::get)
        user.studentCategory = spec.studentCategory
        user.accountStatus = spec.accountStatus

        if (spec.accountStatus == AccountStatus.FROZEN_EXPELLED) {
            user.expelledAt = rosterWeekPolicy.now()
            user.expelledBy = expelledBy
            user.expelNote = spec.expelNote ?: "Отчислен в тестовом сценарии"
        } else {
            user.expelledAt = null
            user.expelledBy = null
            user.expelNote = null
        }

        return userRepository.save(user)
    }

    private fun seedMenu(seedDates: List<LocalDate>) {
        val location = "Столовая 1"
        seedDates.forEach { date ->
            val existingNames = menuRepository.findAllByDateAndLocationIgnoreCase(date, location)
                .map { it.name.lowercase() }
                .toSet()
            val toCreate = listOf(
                MenuEntity(
                    date = date,
                    name = "Завтрак тестовый",
                    location = location,
                    description = "Каша, хлеб, чай",
                ),
                MenuEntity(
                    date = date,
                    name = "Обед тестовый",
                    location = location,
                    description = "Суп, горячее, компот",
                ),
            ).filterNot { it.name.lowercase() in existingNames }
            if (toCreate.isNotEmpty()) {
                menuRepository.saveAll(toCreate)
            }
        }
    }

    private fun seedPermissions(
        seedDates: List<LocalDate>,
        curator: UserEntity,
        student1011: UserEntity,
        student1012: UserEntity,
        student1013: UserEntity,
        student1014: UserEntity,
        student1015: UserEntity,
        student1021: UserEntity,
        student1022: UserEntity,
        studentTest: UserEntity,
    ) {
        val demoStudents = listOf(
            student1011,
            student1012,
            student1013,
            student1014,
            student1015,
            student1021,
            student1022,
            studentTest,
        )
        val startDate = seedDates.firstOrNull() ?: return
        val endDate = seedDates.lastOrNull() ?: return

        val existing = mealPermissionRepository.findAllByStudentInAndDateBetween(demoStudents, startDate, endDate)
        if (existing.isNotEmpty()) {
            mealPermissionRepository.deleteAllInBatch(existing)
        }

        val seededPermissions = mutableListOf<MealPermissionEntity>()
        seedDates.forEach { date ->
            val weekStart = rosterWeekPolicy.weekStart(date)
            val weekEnd = weekStart.plusDays(4)

            seededPermissions += MealPermissionEntity(
                date = date,
                student = student1011,
                assignedBy = curator,
                reason = "Обычный рацион",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
            )
            seededPermissions += MealPermissionEntity(
                date = date,
                student = student1012,
                assignedBy = curator,
                reason = "Больничный",
                isBreakfastAllowed = false,
                isLunchAllowed = false,
                noMealReasonType = NoMealReasonType.SICK_LEAVE,
                absenceFrom = weekStart,
                absenceTo = weekEnd,
            )
            seededPermissions += MealPermissionEntity(
                date = date,
                student = student1013,
                assignedBy = curator,
                reason = "Отчислен",
                isBreakfastAllowed = false,
                isLunchAllowed = false,
                noMealReasonType = NoMealReasonType.EXPELLED,
            )
            seededPermissions += MealPermissionEntity(
                date = date,
                student = student1014,
                assignedBy = curator,
                reason = "Иное",
                isBreakfastAllowed = false,
                isLunchAllowed = false,
                noMealReasonType = NoMealReasonType.OTHER,
                noMealReasonText = "Учебная практика вне колледжа",
                absenceFrom = weekStart,
                absenceTo = weekEnd,
            )
            seededPermissions += MealPermissionEntity(
                date = date,
                student = student1015,
                assignedBy = curator,
                reason = "Назначено, без факта",
                isBreakfastAllowed = true,
                isLunchAllowed = false,
            )
            seededPermissions += MealPermissionEntity(
                date = date,
                student = studentTest,
                assignedBy = curator,
                reason = "Тестовый студент: завтрак и обед",
                isBreakfastAllowed = true,
                isLunchAllowed = true,
            )
        }

        mealPermissionRepository.saveAll(seededPermissions)
    }

    private fun ensureTodayPermission(
        student: UserEntity,
        assignedBy: UserEntity,
        reason: String,
    ) {
        val today = rosterWeekPolicy.today()
        val permission = mealPermissionRepository.findByStudentAndDate(student, today) ?: MealPermissionEntity(
            date = today,
            student = student,
            assignedBy = assignedBy,
            reason = reason,
            isBreakfastAllowed = true,
            isLunchAllowed = true,
        )

        permission.reason = reason
        permission.isBreakfastAllowed = true
        permission.isLunchAllowed = true
        permission.noMealReasonType = null
        permission.noMealReasonText = null
        permission.absenceFrom = null
        permission.absenceTo = null
        permission.comment = null

        mealPermissionRepository.save(permission)
    }

    private fun seedTransactions(
        seedDates: List<LocalDate>,
        chef: UserEntity,
        studentWithFacts: UserEntity,
    ) {
        val existingDemoTransactions = mealTransactionRepository.findAllByTransactionHashStartingWith(DEMO_TX_PREFIX)
        if (existingDemoTransactions.isNotEmpty()) {
            mealTransactionRepository.deleteAllInBatch(existingDemoTransactions)
        }

        val currentWeekStart = rosterWeekPolicy.weekStart(rosterWeekPolicy.today())
        val today = rosterWeekPolicy.today()
        val currentTxDate = when {
            rosterWeekPolicy.isWeekday(today) && seedDates.contains(today) -> today
            else -> currentWeekStart
        }
        val previousTxDate = currentWeekStart.minusWeeks(1)

        val transactions = listOf(
            MealTransactionEntity(
                transactionHash = "${DEMO_TX_PREFIX}${currentTxDate}_stud_Group-101_1_BREAKFAST",
                timeStamp = currentTxDate.atTime(LocalTime.of(8, 35)),
                student = studentWithFacts,
                chef = chef,
                isOffline = false,
                mealType = MealType.BREAKFAST,
            ),
            MealTransactionEntity(
                transactionHash = "${DEMO_TX_PREFIX}${currentTxDate}_stud_Group-101_1_LUNCH",
                timeStamp = currentTxDate.atTime(LocalTime.of(12, 25)),
                student = studentWithFacts,
                chef = chef,
                isOffline = false,
                mealType = MealType.LUNCH,
            ),
            MealTransactionEntity(
                transactionHash = "${DEMO_TX_PREFIX}${previousTxDate}_stud_Group-101_1_BREAKFAST",
                timeStamp = previousTxDate.atTime(LocalTime.of(8, 45)),
                student = studentWithFacts,
                chef = chef,
                isOffline = true,
                mealType = MealType.BREAKFAST,
            ),
        )

        mealTransactionRepository.saveAll(transactions)
    }

    private data class DemoUserSpec(
        val login: String,
        val roles: Set<Role>,
        val name: String,
        val surname: String,
        val fatherName: String,
        val groupName: String?,
        val studentCategory: StudentCategory?,
        val accountStatus: AccountStatus,
        val expelNote: String? = null,
    )

    private companion object {
        const val DEMO_PASSWORD = "password"
        const val DEMO_TX_PREFIX = "demo_"

        const val LOGIN_ADMIN = "admin"
        const val LOGIN_CHEF = "chef_main"
        const val LOGIN_REGISTRATOR = "registrator"
        const val LOGIN_CURATOR_101 = "curator_Group-101"
        const val LOGIN_STUDENT_TEST = "student_test"
    }
}
