package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Entity
@Table(name = "assignment")
data class Assignment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Column(nullable = false)
    @field:NotBlank(message = "{assignment.name.required}")
    val name: String,

    @Column
    val description: String?,

    @Column(name = "dir_name", nullable = false)
    val dirName: String = "",

    @Column(name = "has_starter_code", nullable = false)
    val hasStarterCode: Boolean = false,

    @Column
    @field:NotNull(message = "{kickoff.date.required}")
    val kickoffDate: LocalDateTime,

    @Column
    @field:NotNull(message = "{deadline.date.required}")
    val deadlineDate: LocalDateTime,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
