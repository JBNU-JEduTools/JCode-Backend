package org.jbnu.jdevops.jcodeportallogin.dto.jcode

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.jbnu.jdevops.jcodeportallogin.entity.JcodeLifecycleStatus

data class JCodeDto(
    val jcodeId: Long?,

    @field:NotBlank(message = "{course.name.required}")
    @field:Size(max = 100, message = "{course.name.size}")
    val courseName: String,
    val status: JcodeLifecycleStatus = JcodeLifecycleStatus.READY,
    val jcodeUrl: String? = null,
    val assignmentId: Long? = null,
    val lastError: String? = null
)


data class JCodeMainRequestDto(
    @field:NotBlank(message = "{userDto.email.required}")
    @field:Size(max = 100, message = "{userDto.email.size}")
    val userEmail: String,

    val snapshot: Boolean = false,
    val assignmentId: Long? = null
)
