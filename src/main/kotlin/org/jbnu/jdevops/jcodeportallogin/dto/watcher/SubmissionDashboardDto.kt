package org.jbnu.jdevops.jcodeportallogin.dto.watcher

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
    val flags: List<String>
)

data class SubmissionDashboardDto(
    val assignmentName: String,
    val totalStudents: Int,
    val submittedCount: Int,
    val flaggedCount: Int,
    val students: List<StudentSubmissionSummary>
)
