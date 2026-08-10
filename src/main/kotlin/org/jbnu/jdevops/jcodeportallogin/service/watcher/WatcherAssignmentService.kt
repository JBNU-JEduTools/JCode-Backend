package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.jbnu.jdevops.jcodeportallogin.dto.watcher.AssingmentTotalGraphData
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.AssingmentTotalGraphListData
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.WatcherAssignmentDto
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.WatcherLogAvgDto
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class WatcherAssignmentService(
    @Qualifier("watcherWebClient")
    private val webClient: WebClient,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository
) {
    fun getAssignmentsData(courseId: Long, assignmentId: Long): WatcherAssignmentDto? {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        val classDiv = "${course.code.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/assignment/{class_div}/{hw_name}")
                        .build(classDiv, assignment.watcherHwName())
                }
                .retrieve()
                .bodyToMono(WatcherAssignmentDto::class.java)
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    fun getAssignmentsTotalGraphData(email: String, courseId: Long, assignmentId: Long, st: LocalDateTime, end: LocalDateTime): AssingmentTotalGraphListData? {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        val classDiv = "${course.code.lowercase()}-${course.clss}"

        return try {
            val graphData = webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/total_graph_data/{class_div}/{hw_name}/{st}/{end}")
                        .build(classDiv, assignment.watcherHwName(), st, end)
                }
                .retrieve()
                .bodyToMono(AssingmentTotalGraphListData::class.java)
                .block()

            // 유저의 role에 따라 반환 데이터를 달리 가공
            when (user.role) {
                RoleType.STUDENT -> {
                    // 해당 강의에서의 조교 권한 확인
                    val userCourse = userCoursesRepository.findByUserIdAndCourseId(user.id, courseId)
                    if (userCourse?.role == RoleType.ASSISTANT) {
                        graphData
                    } else {
                        val totalStudents = userCoursesRepository.countUserCoursesByCourseIdAndRole(courseId, RoleType.STUDENT)
                        modifyGraphDataForStudent(graphData, user.studentNum, totalStudents, courseId)
                    }
                }
                else -> {  // ADMIN과 PROFESSOR은 전부 반환
                    graphData
                }
            }
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    // 학생용 그래프 데이터는 자신의 학번을 제외한 다른 학생의 학번 확인 불가능하도록 변경
    private fun modifyGraphDataForStudent(data: AssingmentTotalGraphListData?, myStudentNum: Int?, totalStudents: Int, courseId: Long): AssingmentTotalGraphListData {
        var myDataUsed: Boolean = false
        if (myStudentNum == null) myDataUsed = true

        // 기존 데이터 항목을 순차적인 번호로 변환
        val initialList = data?.results?.mapIndexedNotNull { index, graph ->
            val usercourse = userCoursesRepository.findByUserStudentNumAndCourseId(graph.student_num, courseId)

            if (usercourse != null && usercourse.role == RoleType.STUDENT) {
                if (!myDataUsed && graph.student_num == myStudentNum) {
                    // 자신의 데이터는 실제 학번 유지
                    myDataUsed = true
                    AssingmentTotalGraphData(student_num = graph.student_num, size_change = graph.size_change)
                } else {
                    // 다른 학생의 경우, 실제 학번 대신 순차적으로 번호 부여
                    AssingmentTotalGraphData(student_num = index + 1, size_change = graph.size_change)
                }
            } else {
                // 탈퇴한 학생의 경우, null 반환하여 리스트에서 제외
                null
            }
        } ?: emptyList()

        // 현재 데이터 항목 수가 강의에 가입한 전체 학생 수보다 작으면 추가
        val mutableList = initialList.toMutableList()
        val currentCount = mutableList.size
        if (currentCount < totalStudents) {
            for (index in currentCount until totalStudents) {
                // 데이터가 없는 학생에 대해 순차적인 번호 부여 및 size_change 0 설정
                if (!myDataUsed) {
                    myDataUsed = true
                    mutableList.add(AssingmentTotalGraphData(student_num = myStudentNum?: (index+1), size_change = 0L))
                } else {
                    mutableList.add(AssingmentTotalGraphData(student_num = index + 1, size_change = 0L))
                }
            }
        }
        return AssingmentTotalGraphListData(mutableList)
    }

    fun getBuildLogAvg(courseId: Long, assignmentId: Long): WatcherLogAvgDto? {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        val classDiv = "${course.code.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/build_avg/{class_div}/{hw_name}")
                        .build(classDiv, assignment.watcherHwName())
                }
                .retrieve()
                .bodyToMono(WatcherLogAvgDto::class.java)
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }

    fun getRunLogAvg(courseId: Long, assignmentId: Long): WatcherLogAvgDto? {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        val classDiv = "${course.code.lowercase()}-${course.clss}"

        return try {
            webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/run_avg/{class_div}/{hw_name}")
                        .build(classDiv, assignment.watcherHwName())
                }
                .retrieve()
                .bodyToMono(WatcherLogAvgDto::class.java)
                .block()
        } catch (ex: Exception) {
            println("Error calling external API: ${ex.message}")
            null
        }
    }
}
