package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

@Entity
@Table(name = "course")
data class Course(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    @field:NotBlank(message = "{course.name.required}")
    @field:Size(max = 100, message = "{course.name.size}")
    val name: String,

    @Column(nullable = false)
    @field:NotBlank(message = "{course.code.required}")
    @field:Size(max = 20, message = "{course.code.size}")
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "{course.code.pattern}")
    val code: String,

    @Column(nullable = false)
    val year: Int,

    @Column(nullable = false)
    val term: Int,

    @Column(nullable = false)
    @field:NotBlank(message = "{professor.name.required}")
    @field:Size(max = 50, message = "{professor.name.size}")
    val professor: String,

    @Column(nullable = false)
    val clss: Int,

    // Non-archived courses reserve their Kubernetes namespace through this key.
    // It is nullable so a failed duplicate record or archived history can remain.
    @Column(name = "namespace_key", length = 63, unique = true)
    var namespaceKey: String? = null,

    @Column(nullable = false)
    val vnc: Boolean,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val environmentProfile: CourseEnvironmentProfile = if (vnc) CourseEnvironmentProfile.LAB else CourseEnvironmentProfile.ALGORITHM,

    @Column(nullable = false)
    val useVnc: Boolean = vnc,

    @Column(nullable = false)
    val useJupyter: Boolean = vnc,

    @Column(length = 512)
    val baseImage: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val resourceProfile: WorkspaceResourceProfile = WorkspaceResourceProfile.STANDARD,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val egressPolicy: WorkspaceEgressPolicy = WorkspaceEgressPolicy.PACKAGE_PROXY,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val workspaceScope: WorkspaceScope = WorkspaceScope.COURSE,

    @Column(nullable = false)
    val hwCount: Int = 10,

    @Column(nullable = false)
    val pracEnabled: Boolean = false,

    @Column(nullable = false)
    val pracCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CourseStatus = CourseStatus.ACTIVE,

    @Column
    var endedAt: LocalDateTime? = null,

    @Column(nullable = false)
    @field:NotBlank(message = "{course.key.required}")
    @field:Size(max = 100, message = "{course.key.size}")
    var courseKey: String,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val userCourses: List<UserCourses> = mutableListOf(),

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var assignments: MutableList<Assignment> = mutableListOf()
) {
    companion object {
        fun namespaceKey(code: String, clss: Int) = "jcode-${code.trim().lowercase()}-$clss"
    }
}
