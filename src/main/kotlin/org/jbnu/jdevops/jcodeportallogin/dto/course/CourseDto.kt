package org.jbnu.jdevops.jcodeportallogin.dto.course

import jakarta.validation.constraints.NotBlank
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
    val code: String,

    @field:NotBlank(message = "{professor.name.required}")
    @field:Size(max = 50, message = "{professor.name.size}")
    val professor: String,

    val year: Int,
    val term: Int,
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
    val hwCount: Int = 10,
    val pracEnabled: Boolean = false,
    val pracCount: Int = 0,
    val status: CourseStatus = CourseStatus.ACTIVE,
    val endedAt: String? = null,

    @field:NotBlank(message = "{course.key.required}")
    @field:Size(max = 100, message = "{course.key.size}")
    val courseKey: String? = "hidden"
)
