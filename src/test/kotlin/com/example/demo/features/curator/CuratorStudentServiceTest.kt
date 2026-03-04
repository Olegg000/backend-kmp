package com.example.demo.features.curator

import com.example.demo.config.TestProfileResolver
import com.example.demo.config.TimeConfig
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.repository.GroupRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.curator.dto.CuratorStudentCategoryUpdateRequest
import com.example.demo.features.curator.service.CuratorStudentService
import com.example.demo.features.roster.service.RosterWeekPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@Import(CuratorStudentService::class, RosterWeekPolicy::class, TimeConfig::class)
@ActiveProfiles(resolver = TestProfileResolver::class)
@DisplayName("CuratorStudentService - смена категории и список")
class CuratorStudentServiceTest(
    @Autowired private val curatorStudentService: CuratorStudentService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val groupRepository: GroupRepository,
) {

    private lateinit var group1: GroupEntity
    private lateinit var group2: GroupEntity
    private lateinit var curator1: UserEntity
    private lateinit var curator2: UserEntity
    private lateinit var studentGroup1: UserEntity

    @BeforeEach
    fun setup() {
        group1 = groupRepository.save(GroupEntity(groupName = "ИСП-31"))
        group2 = groupRepository.save(GroupEntity(groupName = "ИСП-32"))

        curator1 = userRepository.save(
            UserEntity(
                login = "curator-1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Мария",
                surname = "Первая",
                fatherName = "А"
            )
        )

        curator2 = userRepository.save(
            UserEntity(
                login = "curator-2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.CURATOR),
                name = "Ирина",
                surname = "Вторая",
                fatherName = "Б"
            )
        )

        group1.curators = mutableSetOf(curator1)
        group2.curators = mutableSetOf(curator2)
        groupRepository.save(group1)
        groupRepository.save(group2)

        studentGroup1 = userRepository.save(
            UserEntity(
                login = "student-g1",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Петр",
                surname = "Студентов",
                fatherName = "В",
                group = group1,
                studentCategory = StudentCategory.MANY_CHILDREN
            )
        )
    }

    @Test
    @DisplayName("Куратор меняет категорию своего студента")
    fun `curator can update category of own student`() {
        val updated = curatorStudentService.updateStudentCategory(
            curatorLogin = curator1.login,
            studentId = studentGroup1.id!!,
            request = CuratorStudentCategoryUpdateRequest(StudentCategory.SVO)
        )

        assertEquals(StudentCategory.SVO, updated.studentCategory)

        val fromDb = userRepository.findById(studentGroup1.id!!).orElseThrow()
        assertEquals(StudentCategory.SVO, fromDb.studentCategory)
    }

    @Test
    @DisplayName("Куратор не может менять категорию студента чужой группы")
    fun `curator cannot update category of foreign student`() {
        val foreignStudent = userRepository.save(
            UserEntity(
                login = "student-g2",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Анна",
                surname = "Внешняя",
                fatherName = "Г",
                group = group2,
                studentCategory = StudentCategory.MANY_CHILDREN
            )
        )

        val ex = assertThrows(RuntimeException::class.java) {
            curatorStudentService.updateStudentCategory(
                curatorLogin = curator1.login,
                studentId = foreignStudent.id!!,
                request = CuratorStudentCategoryUpdateRequest(StudentCategory.SVO)
            )
        }

        assertTrue(ex.message!!.contains("только студентов своих групп"))
    }

    @Test
    @DisplayName("Список студентов куратора поддерживает студентов без категории")
    fun `list students should include null category`() {
        userRepository.save(
            UserEntity(
                login = "student-no-category",
                passwordHash = "hash",
                roles = mutableSetOf(Role.STUDENT),
                name = "Сергей",
                surname = "БезКатегории",
                fatherName = "Д",
                group = group1,
                studentCategory = null
            )
        )

        val rows = curatorStudentService.listMyStudents(curator1.login)
        val row = rows.firstOrNull { it.fullName.contains("БезКатегории") }

        assertTrue(row != null)
        assertEquals(null, row?.studentCategory)
    }
}
