package org.jbnu.jdevops.jcodeportallogin.dto.watcher

enum class WatcherStatus {
    OK,
    PARTIAL,
    UNAVAILABLE
}

data class StudentSubmissionSummary(
    val studentNum: Int,
    val studentName: String?,
    val buildCount: Int,
    val buildFailCount: Int,
    val runCount: Int,
    val totalSizeChange: Long,
    val maxSingleChange: Long,
    val codeVelocity: Double,
    val totalWorkMinutes: Long,
    val firstActivity: String?,
    val lastActivity: String?,
    val flags: List<String>,
    val watcherStatus: WatcherStatus
)

data class SubmissionDashboardDto(
    val assignmentName: String,
    val totalStudents: Int,
    val submittedCount: Int,
    val flaggedCount: Int,
    val watcherStatus: WatcherStatus,
    val students: List<StudentSubmissionSummary>
)
