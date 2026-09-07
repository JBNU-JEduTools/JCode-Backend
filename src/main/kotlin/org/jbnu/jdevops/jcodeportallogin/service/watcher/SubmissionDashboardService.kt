package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.jbnu.jdevops.jcodeportallogin.dto.watcher.StudentSubmissionSummary
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.SubmissionDashboardDto
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.WatcherStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory

internal fun parseWatcherTimestamp(value: String): LocalDateTime =
    runCatching {
        OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
            .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toLocalDateTime()
    }.getOrElse {
        LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

@Service
class SubmissionDashboardService(
    @Qualifier("watcherWebClient")
    private val webClient: WebClient,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private data class WatcherFetch<T>(val value: T, val available: Boolean)

    private data class BulkDashboardRequest(
        @JsonProperty("student_ids") val studentIds: List<Int>
    )

    private data class BulkStudentSummary(
        @JsonProperty("student_id") val studentId: Int,
        @JsonProperty("build_count") val buildCount: Int,
        @JsonProperty("build_fail_count") val buildFailCount: Int,
        @JsonProperty("run_count") val runCount: Int,
        @JsonProperty("total_size_change") val totalSizeChange: Long,
        @JsonProperty("max_single_change") val maxSingleChange: Long,
        @JsonProperty("first_activity") val firstActivity: String?,
        @JsonProperty("last_activity") val lastActivity: String?
    )

    private data class BulkDashboardResponse(val students: List<BulkStudentSummary> = emptyList())

    private fun <T> fetchWatcher(defaultValue: T, description: String, call: () -> T?): WatcherFetch<T> = try {
        WatcherFetch(call() ?: defaultValue, true)
    } catch (ex: Exception) {
        logger.warn("Watcher call failed: {}", description, ex)
        WatcherFetch(defaultValue, false)
    }

    fun getDashboard(courseId: Long, assignmentId: Long, email: String): SubmissionDashboardDto {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        // 전역 역할이 아니라 해당 강의에서의 역할로 권한을 판단한다.
        if (user.role != RoleType.ADMIN) {
            val membership = userCoursesRepository.findByUserIdAndCourseId(user.id, courseId)
                ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의에 소속되어 있지 않습니다.")
            if (membership.role !in setOf(RoleType.PROFESSOR, RoleType.ASSISTANT)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "대시보드 접근 권한이 없습니다.")
            }
        }

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignment = assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        if (assignment.course.id != course.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The assignment does not belong to the specified course")
        }

        val classDiv = "${course.infrastructureKey.lowercase()}-${course.clss}"

        // 해당 과목 학생 목록 조회
        val studentCourses = userCoursesRepository.findByCourseIdAndRole(courseId, RoleType.STUDENT)

        val students = studentCourses.mapNotNull { membership ->
            membership.user.studentNum?.let { it to membership.user.name }
        }
        val watcherResult = if (students.isEmpty()) {
            WatcherFetch(BulkDashboardResponse(), true)
        } else fetchWatcher(BulkDashboardResponse(), "dashboard bulk summary") {
            webClient.post()
                .uri("/api/dashboard/{class_div}/{hw_name}/summary", classDiv, assignment.watcherHwName())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(BulkDashboardRequest(students.map { it.first }))
                .retrieve()
                .bodyToMono(BulkDashboardResponse::class.java)
                .timeout(Duration.ofSeconds(10))
                .block()
        }
        val watcherStudents = watcherResult.value.students.associateBy { it.studentId }
        val summaries = students.map { (studentNum, studentName) ->
            val summary = watcherStudents[studentNum]
            if (!watcherResult.available || summary == null) {
                emptyStudentSummary(studentNum, studentName, WatcherStatus.UNAVAILABLE)
            } else {
                buildStudentSummary(summary, studentName)
            }
        }

        val flaggedCount = summaries.count { it.flags.isNotEmpty() }
        val dashboardStatus = when {
            summaries.isNotEmpty() && summaries.all { it.watcherStatus == WatcherStatus.UNAVAILABLE } -> WatcherStatus.UNAVAILABLE
            summaries.any { it.watcherStatus != WatcherStatus.OK } -> WatcherStatus.PARTIAL
            else -> WatcherStatus.OK
        }

        return SubmissionDashboardDto(
            assignmentName = assignment.name,
            totalStudents = studentCourses.size,
            submittedCount = summaries.count { it.buildCount > 0 || it.totalSizeChange > 0 },
            flaggedCount = flaggedCount,
            watcherStatus = dashboardStatus,
            students = summaries
        )
    }

    private fun buildStudentSummary(summary: BulkStudentSummary, studentName: String?): StudentSubmissionSummary {
        val watcherStatus = WatcherStatus.OK
        val buildCount = summary.buildCount
        val buildFailCount = summary.buildFailCount
        val runCount = summary.runCount
        val totalSizeChange = summary.totalSizeChange
        val maxSingleChange = summary.maxSingleChange
        val firstActivity = summary.firstActivity?.let(::parseWatcherTimestamp)
        val lastActivity = summary.lastActivity?.let(::parseWatcherTimestamp)
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

        if (watcherStatus == WatcherStatus.OK && buildCount == 0 && runCount == 0 && totalSizeChange == 0L) {
            flags.add("⚠️ 활동 없음")
        }

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
            studentNum = summary.studentId,
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
            flags = flags,
            watcherStatus = watcherStatus
        )
    }

    private fun emptyStudentSummary(studentNum: Int, studentName: String?, status: WatcherStatus) =
        StudentSubmissionSummary(
            studentNum = studentNum,
            studentName = studentName,
            buildCount = 0,
            buildFailCount = 0,
            runCount = 0,
            totalSizeChange = 0,
            maxSingleChange = 0,
            codeVelocity = 0.0,
            totalWorkMinutes = 0,
            firstActivity = null,
            lastActivity = null,
            flags = emptyList(),
            watcherStatus = status
        )
}
