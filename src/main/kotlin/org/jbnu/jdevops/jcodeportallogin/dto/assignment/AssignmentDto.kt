package org.jbnu.jdevops.jcodeportallogin.dto.assignment

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class AssignmentDto(
    val assignmentId: Long? = null,

    @field:NotBlank(message = "{assignment.name.required}")
    @field:Size(max = 50, message = "{assignment.name.size}")
    val assignmentName: String,

    @field:Size(max = 500, message = "{assignment.description.size}")
    val assignmentDescription: String? = null,

    val dirName: String? = null,
    val hasStarterCode: Boolean? = null,

    @field:NotNull(message = "{kickoff.date.required}")
    val kickoffDate: LocalDateTime,

    @field:NotNull(message = "{deadline.date.required}")
    val deadlineDate: LocalDateTime,

    val createdAt: String? = null,
    val updatedAt: String? = null
)
