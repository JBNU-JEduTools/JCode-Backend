package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class WorkspaceOperationTarget { ASSIGNMENT, MEMBERSHIP, JCODE }
enum class WorkspaceOperationAction {
    MIGRATE_ASSIGNMENT_PATH,
    PROVISION_ASSIGNMENT,
    UPDATE_ASSIGNMENT_METADATA,
    DISTRIBUTE_STARTER,
    ARCHIVE_FINAL_SUBMISSION,
    RESTORE_ASSIGNMENT,
    ARCHIVE_ASSIGNMENT,
    PROVISION_MEMBERSHIP,
    DELETE_MEMBERSHIP,
    PROVISION_JCODE,
    DELETE_JCODE
}
enum class WorkspaceOperationStatus { PENDING, PROCESSING, SUCCEEDED, FAILED }

@Entity
@Table(name = "workspace_operation")
class WorkspaceOperation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 24)
    val targetType: WorkspaceOperationTarget,

    @Column(name = "target_id", nullable = false)
    val targetId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val action: WorkspaceOperationAction,

    @Column(name = "artifact_id")
    val artifactId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: WorkspaceOperationStatus = WorkspaceOperationStatus.PENDING,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    val idempotencyKey: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "locked_at")
    var lockedAt: LocalDateTime? = null,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
