package com.example.demo.core.database.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table

@Entity
@Table(name = "groups")
class GroupEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "group_name", unique = true, nullable = false)
    val groupName: String,

    @ManyToMany
    @JoinTable(
        name = "group_curators",
        joinColumns = [JoinColumn(name = "group_id")],
        inverseJoinColumns = [JoinColumn(name = "curator_id")]
    )
    var curators: MutableSet<UserEntity> = mutableSetOf()

)
