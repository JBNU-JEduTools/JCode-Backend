package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.util.AuthorizationUtil
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException

@Service
class WatcherSelectionService (
    @Qualifier("watcherWebClient")
    private val webClient: WebClient,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository
) {
    fun getFileSelections(email: String, courseId: Long, assignmentId: Long, userId: Long): List<String>? {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current User not found")

        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        AuthorizationUtil.validateUserAuthority(currentUser.role, currentUser.id, targetUser.id, course.id, userCoursesRepository)

        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        if (!userCoursesRepository.existsByUserIdAndCourseId(targetUser.id, course.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserCourse not found")
        }

        val classDiv = "${course.code.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/{class_div}/{hw_name}/{student_num}")
                        .build(classDiv, assignment.watcherHwName(), targetUser.studentNum)
                }
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<List<String>>() {})  // List 파싱
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    fun getTimestampSelections(email: String, filename: String, courseId: Long, assignmentId: Long, userId: Long): List<String>? {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current User not found")

        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        AuthorizationUtil.validateUserAuthority(currentUser.role, currentUser.id, targetUser.id, course.id, userCoursesRepository)

        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        if (!userCoursesRepository.existsByUserIdAndCourseId(targetUser.id, course.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserCourse not found")
        }

        val classDiv = "${course.code.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/{class_div}/{hw_name}/{student_num}/{filename}")
                        .build(classDiv, assignment.watcherHwName(), targetUser.studentNum, filename)
                }
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<List<String>>() {})  // List 파싱
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }
}
