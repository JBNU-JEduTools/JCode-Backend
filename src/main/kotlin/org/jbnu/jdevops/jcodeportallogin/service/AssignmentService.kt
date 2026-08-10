package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.jbnu.jdevops.jcodeportallogin.config.GENERATOR_SCOPE_ATTRIBUTE
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AssignmentService(
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository,
    @Qualifier("generatorWorkspaceWebClient")
    private val generatorWebClient: WebClient
) {

    private fun validateAssignmentAuthority(courseId: Long, email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        if (user.role == RoleType.ADMIN) {
            return
        }

        val membership = userCoursesRepository.findByUserIdAndCourseId(user.id, courseId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의에 소속되어 있지 않습니다.")

        if (membership.role !in setOf(RoleType.PROFESSOR, RoleType.ASSISTANT)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 과제 관리 권한이 없습니다.")
        }
    }

    private fun getActiveCourse(courseId: Long): Course {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }
        if (course.status != CourseStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE 상태의 강의에서만 과제를 변경할 수 있습니다.")
        }
        return course
    }

    private fun getAssignmentInCourse(courseId: Long, assignmentId: Long): Assignment =
        assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found in course") }

    private fun toDirName(name: String): String {
        return name.trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
            .take(80)
    }

    private fun provisionAssignmentDirectory(courseId: Long, courseCode: String, clss: Int, dirName: String) {
        generatorWebClient.post()
            .uri("/api/workspace/provision")
            .attribute(GENERATOR_SCOPE_ATTRIBUTE, "workspace:write")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf(
                "course_id" to courseId,
                "namespace" to "jcode-${courseCode.lowercase()}-$clss",
                "dir_name" to dirName
            ))
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()
    }

    // 과제 추가
    @Transactional
    fun createAssignment(courseId: Long, assignmentDto: AssignmentDto, email: String, token: String): AssignmentDto {
        validateAssignmentAuthority(courseId, email)

        val course = getActiveCourse(courseId)

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

        provisionAssignmentDirectory(course.id, course.code, course.clss, dirName)

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

        getActiveCourse(courseId)
        val assignment = getAssignmentInCourse(courseId, assignmentId)

        // dirName은 변경하지 않음 (디렉토리명 불변)
        val updatedAssignment = assignment.copy(
            name = assignmentDto.assignmentName,
            description = assignmentDto.assignmentDescription,
            kickoffDate = assignmentDto.kickoffDate,
            deadlineDate = assignmentDto.deadlineDate,
            updatedAt = LocalDateTime.now()
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

        val course = getActiveCourse(courseId)
        val assignment = getAssignmentInCourse(courseId, assignmentId)

        // Generator에 스타터 코드 배포 요청
        val result = try {
            generatorWebClient.post()
                .uri("/api/workspace/starter-code")
                .attribute(GENERATOR_SCOPE_ATTRIBUTE, "workspace:write")
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .bodyValue(
                    org.springframework.util.LinkedMultiValueMap<String, Any>().apply {
                        add("course_id", course.id)
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

        val deployed = (result?.get("deployed") as? Number)?.toInt() ?: 0
        if (deployed == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "스타터 코드를 배포할 학생 워크스페이스가 없습니다.")
        }

        // hasStarterCode 플래그 업데이트
        assignmentRepository.save(assignment.copy(hasStarterCode = true, updatedAt = LocalDateTime.now()))
    }

    // 과제 삭제
    @Transactional
    fun deleteAssignment(courseId: Long, assignmentId: Long, email: String) {
        validateAssignmentAuthority(courseId, email)
        getActiveCourse(courseId)
        val assignment = getAssignmentInCourse(courseId, assignmentId)
        assignmentRepository.delete(assignment)
    }
}
