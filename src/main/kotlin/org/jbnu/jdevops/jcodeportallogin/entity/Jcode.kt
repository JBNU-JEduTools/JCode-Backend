package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

enum class JcodeKind { STANDARD, SNAPSHOT }
enum class JcodeLifecycleStatus { PROVISIONING, PROVISION_FAILED, READY, DELETE_PENDING, DELETE_FAILED, ARCHIVED }
enum class JcodeObservedStatus { UNKNOWN, READY, NOT_READY, MISSING, FAILED, DRIFTED }

@Entity
@Table(name = "jcode")
data class Jcode(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_course_id", nullable = false)
    val userCourse: UserCourses,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(optional = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id")
    val assignment: Assignment? = null,

    @Column
    var jcodeUrl: String? = null,

    @Column(nullable = false)
    val snapshot: Boolean = false,

    @Column(name = "instance_key", nullable = false, length = 96)
    val instanceKey: String = "${userCourse.id}:${assignment?.id ?: 0}:$snapshot",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val kind: JcodeKind = if (snapshot) JcodeKind.SNAPSHOT else JcodeKind.STANDARD,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 24)
    var lifecycleStatus: JcodeLifecycleStatus = JcodeLifecycleStatus.PROVISIONING,

    @Column(name = "deployment_name", nullable = false, length = 63)
    val deploymentName: String = if (snapshot) {
        "jcode-snapshot-${course.infrastructureKey.lowercase()}-${user.studentNum}"
    } else {
        "jcode-${course.infrastructureKey.lowercase()}-${course.clss}-${user.studentNum}"
    },

    @Column(name = "service_name", nullable = false, length = 63)
    val serviceName: String = "$deploymentName-svc",

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "observed_status", nullable = false, length = 24)
    var observedStatus: JcodeObservedStatus = JcodeObservedStatus.UNKNOWN,

    @Column(name = "observed_reason", length = 128)
    var observedReason: String? = null,

    @Column(name = "last_observed_at")
    var lastObservedAt: LocalDateTime? = null,

    @Column(name = "archived_at")
    var archivedAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
