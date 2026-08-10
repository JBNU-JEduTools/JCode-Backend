package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

enum class CourseInfrastructureAction {
    PROVISION_NAMESPACE,
    DELETE_WORKLOADS,
    DELETE_NAMESPACE
}

enum class CourseInfrastructureOperationStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED
}

@Entity
@Table(name = "course_infrastructure_operation")
class CourseInfrastructureOperation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    val courseId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val action: CourseInfrastructureAction,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: CourseInfrastructureOperationStatus = CourseInfrastructureOperationStatus.PENDING,

    @Column(nullable = false, unique = true, length = 36)
    val idempotencyKey: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(nullable = false)
    var nextAttemptAt: LocalDateTime = LocalDateTime.now(),

    var lockedAt: LocalDateTime? = null,

    @Column(columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
