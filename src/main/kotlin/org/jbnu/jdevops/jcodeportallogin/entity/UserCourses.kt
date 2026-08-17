package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

enum class MembershipStatus {
    PROVISIONING,
    PROVISION_FAILED,
    READY,
    DELETE_PENDING,
    DELETE_FAILED,
    ARCHIVED
}

@Entity
@Table(
    name = "user_courses",
    uniqueConstraints = [  // coure_id, user_id 쌍 unique 제약조건 설정
        UniqueConstraint(columnNames = ["course_id", "user_id"])
    ]
)
data class UserCourses(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    @field:NotNull(message = "{userCourses.course.required}")
    var course: Course,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @field:NotNull(message = "{userCourses.user.required}")
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @field:NotNull(message = "{userCourses.role.required}")
    var role: RoleType,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 24)
    var lifecycleStatus: MembershipStatus = MembershipStatus.PROVISIONING,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "archived_at")
    var archivedAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "userCourse", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    val jcodes: List<Jcode> = mutableListOf(),

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
