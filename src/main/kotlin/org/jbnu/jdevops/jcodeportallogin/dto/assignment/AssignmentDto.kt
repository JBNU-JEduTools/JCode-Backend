package org.jbnu.jdevops.jcodeportallogin.dto.assignment

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentScheduleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.StarterOverwritePolicy

data class AssignmentDto(
    val assignmentId: Long? = null,

    @field:NotBlank(message = "{assignment.name.required}")
    @field:Size(max = 50, message = "{assignment.name.size}")
    val assignmentName: String,

    @field:Size(max = 500, message = "{assignment.description.size}")
    val assignmentDescription: String? = null,

    val dirName: String? = null,
    val workspaceKey: String? = null,
    val hasStarterCode: Boolean? = null,
    val lifecycleStatus: AssignmentLifecycleStatus? = null,
    val scheduleStatus: AssignmentScheduleStatus? = null,
    val lastError: String? = null,
    val starterVersion: Int? = null,
    val starterChecksum: String? = null,
    val starterOverwritePolicy: StarterOverwritePolicy? = null,
    val archiveRetentionDays: Int = 90,
    val archivedAt: String? = null,
    val finalizedAt: String? = null,

    @field:NotNull(message = "{kickoff.date.required}")
    val kickoffDate: LocalDateTime,

    @field:NotNull(message = "{deadline.date.required}")
    val deadlineDate: LocalDateTime,

    val createdAt: String? = null,
    val updatedAt: String? = null
)
