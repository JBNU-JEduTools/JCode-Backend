package org.jbnu.jdevops.jcodeportallogin.dto.jcode

import jakarta.validation.constraints.NotBlank

data class RedirectDto (
    @field:NotBlank(message = "{redirect.userEmail.required}")
    val userEmail: String,
    val courseId: Long,
    val snapshot: Boolean = false,
    val assignmentId: Long? = null
)
