package com.example.demo.core.database.entity

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Используем UUID!
    var id: UUID? = null,

    @Column(unique = true, nullable = false)
    var login: String,

    @Column(nullable = false)
    var passwordHash: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    var roles: MutableSet<Role> = mutableSetOf(),

    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var surname: String,
    @Column(nullable = false)
    var fatherName: String,

    @ManyToOne
    @JoinColumn(name = "group_id")
    var group: GroupEntity? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "student_category")
    var studentCategory: StudentCategory? = null,

    @Column(columnDefinition = "TEXT")
    var publicKey: String? = null,

    @Column(columnDefinition = "TEXT")
    var encryptedPrivateKey: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    var accountStatus: AccountStatus = AccountStatus.ACTIVE,

    @Column(name = "expelled_at")
    var expelledAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expelled_by_id")
    var expelledBy: UserEntity? = null,

    @Column(name = "expel_note", columnDefinition = "TEXT")
    var expelNote: String? = null
)
