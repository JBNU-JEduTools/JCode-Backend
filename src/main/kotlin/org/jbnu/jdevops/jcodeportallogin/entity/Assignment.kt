package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

enum class AssignmentLifecycleStatus {
    PROVISIONING,
    ACTIVE,
    PROVISION_FAILED,
    DELETING,
    ARCHIVED
}

enum class AssignmentScheduleStatus {
    SCHEDULED,
    OPEN,
    CLOSED,
    ARCHIVED
}

enum class AssignmentPathBackfillStatus {
    PENDING,
    REGISTERED,
    ARCHIVED
}

@Entity
@Table(
    name = "assignment",
    uniqueConstraints = [UniqueConstraint(name = "uk_assignment_course_name", columnNames = ["course_id", "name"])]
)
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
    var dirName: String = "pending-${UUID.randomUUID()}",

    @Column(name = "workspace_key", nullable = false, unique = true, length = 80)
    var workspaceKey: String = dirName,

    @Column(name = "legacy_dir_name", length = 100)
    var legacyDirName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "path_backfill_status", length = 16)
    var pathBackfillStatus: AssignmentPathBackfillStatus? = null,

    @Column(name = "has_starter_code", nullable = false)
    var hasStarterCode: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 24)
    var lifecycleStatus: AssignmentLifecycleStatus = AssignmentLifecycleStatus.PROVISIONING,

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_status", nullable = false, length = 24)
    var scheduleStatus: AssignmentScheduleStatus = AssignmentScheduleStatus.SCHEDULED,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "archive_retention_days", nullable = false)
    var archiveRetentionDays: Int = 90,

    @Column(name = "archived_at")
    var archivedAt: LocalDateTime? = null,

    @Column(name = "finalized_at")
    var finalizedAt: LocalDateTime? = null,

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
