package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.jbnu.jdevops.jcodeportallogin.dto.watcher.GraphDataListDto
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.SnapshotAvgDto
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.WatcherBuildLogDto
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.WatcherRunLogDto
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.*
import org.jbnu.jdevops.jcodeportallogin.util.AuthorizationUtil
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException

@Service
class WatcherStudentService(
    @Qualifier("watcherWebClient")
    private val webClient: WebClient,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository
) {
    fun getSnapshotAverage(email: String, fileName: String, courseId: Long, assignmentId: Long, userId: Long): SnapshotAvgDto? {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current User not found")

        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        AuthorizationUtil.validateUserAuthority(currentUser.role, currentUser.id, targetUser.id, course.id, userCoursesRepository)

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        if (!userCoursesRepository.existsByUserIdAndCourseId(targetUser.id, course.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserCourse not found")
        }

        val classDiv = "${course.infrastructureKey.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/snapshot_avg/{class_div}/{hw_name}/{student_num}/{fileName}")
                        .build(classDiv, assignment.watcherHwName(), targetUser.studentNum, fileName)
                }
                .retrieve()
                .bodyToMono(SnapshotAvgDto::class.java)
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    fun getAssignmentSnapshotAverage(email: String, courseId: Long, assignmentId: Long, userId: Long): SnapshotAvgDto? {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current User not found")

        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        AuthorizationUtil.validateUserAuthority(currentUser.role, currentUser.id, targetUser.id, course.id, userCoursesRepository)

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        if (!userCoursesRepository.existsByUserIdAndCourseId(targetUser.id, course.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserCourse not found")
        }

        val classDiv = "${course.infrastructureKey.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/assignments/snapshot_avg/{class_div}/{hw_name}/{student_num}")
                        .build(classDiv, assignment.watcherHwName(), targetUser.studentNum)
                }
                .retrieve()
                .bodyToMono(SnapshotAvgDto::class.java)
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    fun getGraphData(email:String, interval: Long, courseId: Long, assignmentId: Long, userId: Long): GraphDataListDto? {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current User not found")

        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        AuthorizationUtil.validateUserAuthority(currentUser.role, currentUser.id, targetUser.id, course.id, userCoursesRepository)

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        if (!userCoursesRepository.existsByUserIdAndCourseId(targetUser.id, course.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserCourse not found")
        }

        val classDiv = "${course.infrastructureKey.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/graph_data/{class_div}/{hw_name}/{student_num}/{interval}")
                        .build(classDiv, assignment.watcherHwName(), targetUser.studentNum, interval)
                }
                .retrieve()
                .bodyToMono(GraphDataListDto::class.java)
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    fun getBuildLogs(email: String, courseId: Long, assignmentId: Long, userId: Long): List<WatcherBuildLogDto>? {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current User not found")

        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        AuthorizationUtil.validateUserAuthority(currentUser.role, currentUser.id, targetUser.id, course.id, userCoursesRepository)

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        if (!userCoursesRepository.existsByUserIdAndCourseId(targetUser.id, course.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserCourse not found")
        }

        val classDiv = "${course.infrastructureKey.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/{class_div}/{hw_name}/{student_num}/logs/build")
                        .build(classDiv, assignment.watcherHwName(), targetUser.studentNum)
                }
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<List<WatcherBuildLogDto>>() {})  // List 파싱
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    fun getRunLogs(email: String, courseId: Long, assignmentId: Long, userId: Long): List<WatcherRunLogDto>? {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current User not found")

        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        AuthorizationUtil.validateUserAuthority(currentUser.role, currentUser.id, targetUser.id, course.id, userCoursesRepository)

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        if (!userCoursesRepository.existsByUserIdAndCourseId(targetUser.id, course.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserCourse not found")
        }

        val classDiv = "${course.infrastructureKey.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/{class_div}/{hw_name}/{student_num}/logs/run")
                        .build(classDiv, assignment.watcherHwName(), targetUser.studentNum)
                }
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<List<WatcherRunLogDto>>() {})  // List 파싱
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }
}
