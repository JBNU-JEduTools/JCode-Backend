package org.jbnu.jdevops.jcodeportallogin.dto.jcode

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class JCodeRequestDto(
    val course_id: Long,

    @field:NotBlank(message = "{jcode.request.namespace.required}")
    @field:Size(max = 50, message = "Namespace must be at most {max} characters")
    val namespace: String,

    @field:NotBlank(message = "{jcode.request.deploymentName.required}")
    @field:Size(max = 50, message = "Deployment name must be at most {max} characters")
    val deployment_name: String,

    @field:NotBlank(message = "{jcode.request.serviceName.required}")
    @field:Size(max = 50, message = "Service name must be at most {max} characters")
    val service_name: String,

    @field:NotBlank(message = "{jcode.request.appLabel.required}")
    @field:Size(max = 50, message = "Application label must be at most {max} characters")
    val app_label: String,

    @field:NotBlank(message = "{jcode.request.filePath.required}")
    @field:Size(max = 200, message = "File path must be at most {max} characters")
    val file_path: String,

    @field:NotBlank(message = "{jcode.request.studentNum.required}")
    @field:Size(max = 20, message = "Student number must be at most {max} characters")
    val student_num: String,

    val use_vnc: Boolean,
    val use_snapshot: Boolean,
    val hw_count: Int = 10,
    val prac_count: Int = 0,
    val assignment_dirs: List<String> = emptyList()
)

data class JCodeDeleteRequestDto(
    val course_id: Long,

    @field:NotBlank(message = "{jcode.delete.namespace.required}")
    @field:Size(max = 50, message = "Namespace must be at most {max} characters")
    val namespace: String,

    @field:NotBlank(message = "{jcode.delete.deploymentName.required}")
    @field:Size(max = 50, message = "Deployment name must be at most {max} characters")
    val deployment_name: String,

    @field:NotBlank(message = "{jcode.delete.serviceName.required}")
    @field:Size(max = 50, message = "Service name must be at most {max} characters")
    val service_name: String
)
