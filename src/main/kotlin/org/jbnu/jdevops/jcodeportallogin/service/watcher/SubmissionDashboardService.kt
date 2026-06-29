package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.jbnu.jdevops.jcodeportallogin.dto.watcher.StudentSubmissionSummary
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.SubmissionDashboardDto
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.WatcherBuildLogDto
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.WatcherRunLogDto
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

@Service
class SubmissionDashboardService(
    @Qualifier("watcherWebClient")
    private val webClient: WebClient,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository
) {

    fun getDashboard(courseId: Long, assignmentId: Long, email: String): SubmissionDashboardDto {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        // 권한 검증: ADMIN, PROFESSOR, 또는 해당 과목 ASSISTANT만 접근 가능
        when (user.role) {
            RoleType.ADMIN, RoleType.PROFESSOR -> { /* OK */ }
            else -> {
                val isAssistant = userCoursesRepository.existsByCourseIdAndUserIdAndRole(courseId, user.id, RoleType.ASSISTANT)
                if (!isAssistant) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "대시보드 접근 권한이 없습니다.")
                }
            }
        }

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        val classDiv = "${course.code.lowercase()}-${course.clss}"

        // 해당 과목 학생 목록 조회
        val studentCourses = userCoursesRepository.findByCourseIdAndRole(courseId, RoleType.STUDENT)

        val summaries = studentCourses.mapNotNull { uc ->
            val student = uc.user
            val sNum = student.studentNum ?: return@mapNotNull null
            try {
                buildStudentSummary(classDiv, assignment.watcherHwName(), sNum, student.name)
            } catch (ex: Exception) {
                StudentSubmissionSummary(
                    studentNum = sNum,
                    studentName = student.name,
                    buildCount = 0,
                    buildFailCount = 0,
                    runCount = 0,
                    totalSizeChange = 0,
                    maxSingleChange = 0,
                    codeVelocity = 0.0,
                    totalWorkMinutes = 0,
                    firstActivity = null,
                    lastActivity = null,
                    flags = listOf("⚠️ 활동 없음")
                )
            }
        }

        val flaggedCount = summaries.count { it.flags.isNotEmpty() }

        return SubmissionDashboardDto(
            assignmentName = assignment.name,
            totalStudents = studentCourses.size,
            submittedCount = summaries.count { it.buildCount > 0 || it.totalSizeChange > 0 },
            flaggedCount = flaggedCount,
            students = summaries
        )
    }

    private fun buildStudentSummary(classDiv: String, hwName: String, studentNum: Int, studentName: String?): StudentSubmissionSummary {
        // 빌드 로그 조회
        val buildLogs = try {
            webClient.get()
                .uri("/api/{class_div}/{hw_name}/{student_num}/logs/build", classDiv, hwName, studentNum)
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<List<WatcherBuildLogDto>>() {})
                .block() ?: emptyList()
        } catch (ex: Exception) { emptyList() }

        // 실행 로그 조회
        val runLogs = try {
            webClient.get()
                .uri("/api/{class_div}/{hw_name}/{student_num}/logs/run", classDiv, hwName, studentNum)
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<List<WatcherRunLogDto>>() {})
                .block() ?: emptyList()
        } catch (ex: Exception) { emptyList() }

        // 그래프 데이터 (코드 크기 변화) 조회 — 1분 간격
        val graphData = try {
            webClient.get()
                .uri("/api/graph_data/{class_div}/{hw_name}/{student_num}/{interval}", classDiv, hwName, studentNum, 1)
                .retrieve()
                .bodyToMono(org.jbnu.jdevops.jcodeportallogin.dto.watcher.GraphDataListDto::class.java)
                .block()
        } catch (ex: Exception) { null }

        val buildCount = buildLogs.size
        val buildFailCount = buildLogs.count { it.exit_code != 0 }
        val runCount = runLogs.size

        // 코드 크기 변화 분석
        val trends = graphData?.trends ?: emptyList()
        val totalSizeChange = trends.sumOf { kotlin.math.abs(it.size_change) }
        val maxSingleChange = trends.maxOfOrNull { kotlin.math.abs(it.size_change) } ?: 0L

        // 시간 분석
        val allTimestamps = buildLogs.map { it.timestamp } + runLogs.map { it.timestamp }
        val firstActivity = allTimestamps.minOrNull()
        val lastActivity = allTimestamps.maxOrNull()
        val totalWorkMinutes = if (firstActivity != null && lastActivity != null) {
            Duration.between(firstActivity, lastActivity).toMinutes()
        } else 0L

        // 코드 작성 속도 (bytes per minute)
        val codeVelocity = if (totalWorkMinutes > 0) {
            totalSizeChange.toDouble() / totalWorkMinutes
        } else if (totalSizeChange > 0) {
            totalSizeChange.toDouble() // 시간 0이면 속도 = 총량 (한 번에 넣은 것)
        } else 0.0

        // 플래그 판정
        val flags = mutableListOf<String>()

        // 1. 컴파일 미시도
        if (buildCount == 0 && totalSizeChange > 0) {
            flags.add("⚠️ 컴파일 미시도")
        }

        // 2. 대량 단일 변경 (한 번 저장에 전체의 70% 이상)
        if (totalSizeChange > 0 && maxSingleChange > 0) {
            val ratio = maxSingleChange.toDouble() / totalSizeChange
            if (ratio >= 0.7) {
                flags.add("⚠️ 대량 단일 변경")
            }
        }

        // 3. 고속 작성 (30분 이내에 200bytes 이상)
        if (totalWorkMinutes in 1..29 && totalSizeChange > 200) {
            flags.add("⚠️ 고속 작성")
        } else if (totalWorkMinutes == 0L && totalSizeChange > 200) {
            flags.add("⚠️ 고속 작성")
        }

        return StudentSubmissionSummary(
            studentNum = studentNum,
            studentName = studentName,
            buildCount = buildCount,
            buildFailCount = buildFailCount,
            runCount = runCount,
            totalSizeChange = totalSizeChange,
            maxSingleChange = maxSingleChange,
            codeVelocity = Math.round(codeVelocity * 100.0) / 100.0,
            totalWorkMinutes = totalWorkMinutes,
            firstActivity = firstActivity?.toString(),
            lastActivity = lastActivity?.toString(),
            flags = flags
        )
    }
}
