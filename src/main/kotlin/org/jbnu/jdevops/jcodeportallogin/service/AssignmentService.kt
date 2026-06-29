package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional

@Service
class AssignmentService(
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository,
    @Qualifier("generatorWebClient")
    private val generatorWebClient: WebClient
) {

    private fun validateAssignmentAuthority(courseId: Long, email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        when (user.role) {
            RoleType.ADMIN, RoleType.PROFESSOR -> return
            RoleType.STUDENT, RoleType.ASSISTANT -> {
                val isAssistant = userCoursesRepository.existsByCourseIdAndUserIdAndRole(courseId, user.id, RoleType.ASSISTANT)
                if (!isAssistant) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 과제 관리 권한이 없습니다.")
                }
            }
        }
    }

    private fun toDirName(name: String): String {
        return name.trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
            .take(80)
    }

    private fun provisionAssignmentDirectory(courseCode: String, clss: Int, dirName: String, token: String) {
        try {
            generatorWebClient.post()
                .uri("/api/workspace/provision")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf(
                    "namespace" to "jcode-${courseCode.lowercase()}-$clss",
                    "dir_name" to dirName
                ))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
        } catch (ex: Exception) {
            println("Warning: Failed to provision workspace directory: ${ex.message}")
        }
    }

    // 과제 추가
    @Transactional
    fun createAssignment(courseId: Long, assignmentDto: AssignmentDto, email: String, token: String): AssignmentDto {
        validateAssignmentAuthority(courseId, email)

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if(assignmentRepository.existsByCourseIdAndName(course.id, assignmentDto.assignmentName)){
            throw ResponseStatusException(HttpStatus.CONFLICT, "Assignment already exists")
        }

        val dirName = toDirName(assignmentDto.assignmentName)
        if (dirName.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 과제명을 입력해주세요.")
        }

        if (assignmentRepository.existsByCourseIdAndDirName(course.id, dirName)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "동일한 디렉토리명의 과제가 이미 존재합니다.")
        }

        val assignment = assignmentRepository.save(Assignment(
            name = assignmentDto.assignmentName,
            description = assignmentDto.assignmentDescription,
            dirName = dirName,
            kickoffDate = assignmentDto.kickoffDate,
            deadlineDate = assignmentDto.deadlineDate,
            course = course))

        provisionAssignmentDirectory(course.code, course.clss, dirName, token)

        return AssignmentDto(
            assignmentId = assignment.id,
            assignmentName = assignment.name,
            assignmentDescription = assignment.description,
            dirName = assignment.dirName,
            hasStarterCode = assignment.hasStarterCode,
            kickoffDate = assignment.kickoffDate,
            deadlineDate = assignment.deadlineDate,
            createdAt = assignment.createdAt.toString(),
            updatedAt = assignment.updatedAt.toString())
    }

    // 과제 수정 (업데이트)
    @Transactional
    fun updateAssignment(courseId: Long, assignmentId: Long, assignmentDto: AssignmentDto, email: String): AssignmentDto {
        validateAssignmentAuthority(courseId, email)

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        // dirName은 변경하지 않음 (디렉토리명 불변)
        val updatedAssignment = assignment.copy(
            name = assignmentDto.assignmentName,
            description = assignmentDto.assignmentDescription,
            kickoffDate = assignmentDto.kickoffDate,
            deadlineDate = assignmentDto.deadlineDate
        )

        assignmentRepository.save(updatedAssignment)

        return AssignmentDto(
            assignmentId = assignment.id,
            assignmentName = updatedAssignment.name,
            assignmentDescription = updatedAssignment.description,
            dirName = updatedAssignment.dirName,
            hasStarterCode = updatedAssignment.hasStarterCode,
            kickoffDate = updatedAssignment.kickoffDate,
            deadlineDate = updatedAssignment.deadlineDate,
            createdAt = updatedAssignment.createdAt.toString(),
            updatedAt = updatedAssignment.updatedAt.toString()
        )
    }

    // 스타터 코드 업로드
    @Transactional
    fun uploadStarterCode(courseId: Long, assignmentId: Long, file: org.springframework.web.multipart.MultipartFile, email: String, token: String) {
        validateAssignmentAuthority(courseId, email)

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        // Generator에 스타터 코드 배포 요청
        try {
            generatorWebClient.post()
                .uri("/api/workspace/starter-code")
                .header("Authorization", "Bearer $token")
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .bodyValue(
                    org.springframework.util.LinkedMultiValueMap<String, Any>().apply {
                        add("namespace", "jcode-${course.code.lowercase()}-${course.clss}")
                        add("dir_name", assignment.dirName)
                        add("file", object : org.springframework.core.io.ByteArrayResource(file.bytes) {
                            override fun getFilename(): String = file.originalFilename ?: "starter.zip"
                        })
                    }
                )
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "스타터 코드 배포 실패: ${ex.message}")
        }

        // hasStarterCode 플래그 업데이트
        assignmentRepository.save(assignment.copy(hasStarterCode = true))
    }

    // 과제 삭제
    @Transactional
    fun deleteAssignment(courseId: Long, assignmentId: Long, email: String) {
        validateAssignmentAuthority(courseId, email)

        if (!assignmentRepository.existsById(assignmentId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found")
        }
        assignmentRepository.deleteById(assignmentId)
    }
}
