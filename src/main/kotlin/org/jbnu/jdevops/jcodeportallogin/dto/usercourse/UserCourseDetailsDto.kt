package org.jbnu.jdevops.jcodeportallogin.dto.usercourse

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType

data class UserCourseDetailsDto(
    val courseId: Long,

    @field:NotBlank(message = "{course.name.required}")
    @field:Size(max = 100, message = "{course.name.size}")
    val courseName: String,

    @field:NotBlank(message = "{course.code.required}")
    @field:Size(max = 20, message = "{course.code.size}")
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "{course.code.pattern}")
    val courseCode: String,

    @field:NotBlank(message = "{professor.name.required}")
    @field:Size(max = 50, message = "{professor.name.size}")
    val courseProfessor: String,

    val courseYear: Int,
    val courseTerm: Int,
    val courseClss: Int,
    val hwCount: Int = 10,
    val pracEnabled: Boolean = false,
    val pracCount: Int = 0,
    val status: CourseStatus = CourseStatus.ACTIVE,
    val courseRole: RoleType? = null,
    val assignments: List<AssignmentDto>,
    val jcodeUrl: String?
)
