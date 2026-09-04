package org.jbnu.jdevops.jcodeportallogin.dto.course

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.CourseEnvironmentProfile
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceEgressPolicy
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceResourceProfile
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceScope

data class CourseDto(
    val courseId: Long?,

    @field:NotBlank(message = "{course.name.required}")
    @field:Size(max = 100, message = "{course.name.size}")
    val name: String,

    @field:NotBlank(message = "{course.code.required}")
    @field:Size(max = 20, message = "{course.code.size}")
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "{course.code.pattern}")
    val code: String,

    @field:NotBlank(message = "{professor.name.required}")
    @field:Size(max = 50, message = "{professor.name.size}")
    val professor: String,

    @field:Min(2000)
    @field:Max(2100)
    val year: Int,
    @field:Min(1)
    @field:Max(2)
    val term: Int,
    @field:Min(1)
    @field:Max(999)
    val clss: Int,
    @Deprecated("environmentProfile과 useVnc를 사용하세요.")
    val vnc: Boolean? = null,
    val environmentProfile: CourseEnvironmentProfile? = null,
    val useVnc: Boolean? = null,
    val useJupyter: Boolean? = null,
    val baseImage: String? = null,
    val resourceProfile: WorkspaceResourceProfile? = null,
    val egressPolicy: WorkspaceEgressPolicy? = null,
    val workspaceScope: WorkspaceScope? = null,
    @field:Min(0)
    @field:Max(100)
    val hwCount: Int = 10,
    val pracEnabled: Boolean = false,
    @field:Min(0)
    @field:Max(10)
    val pracCount: Int = 0,
    val status: CourseStatus = CourseStatus.ACTIVE,
    val endedAt: String? = null,
    val canCancelCreation: Boolean = false,

    @field:NotBlank(message = "{course.key.required}")
    @field:Size(max = 100, message = "{course.key.size}")
    val courseKey: String? = "hidden"
)
