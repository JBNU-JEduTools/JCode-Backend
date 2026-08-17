package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.config.GENERATOR_SCOPE_ATTRIBUTE
import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.entity.*
import org.jbnu.jdevops.jcodeportallogin.repo.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class AssignmentService(
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository,
    private val starterArtifactRepository: StarterArtifactRepository,
    private val jCodeRepository: JCodeRepository,
    private val workspaceOperationStore: WorkspaceOperationStore,
    @Qualifier("generatorWorkspaceWebClient") private val generatorWebClient: WebClient
) {
    private fun validateAssignmentAuthority(courseId: Long, email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        if (user.role == RoleType.ADMIN) return
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

    private fun getAssignment(courseId: Long, assignmentId: Long): Assignment =
        assignmentRepository.findByIdAndCourseId(assignmentId, courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found in course") }

    private fun validateDates(kickoff: LocalDateTime, deadline: LocalDateTime) {
        if (!deadline.isAfter(kickoff)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "마감 시각은 시작 시각보다 뒤여야 합니다.")
        }
    }

    private fun scheduleStatus(kickoff: LocalDateTime, deadline: LocalDateTime): AssignmentScheduleStatus {
        val now = LocalDateTime.now()
        return when {
            now.isBefore(kickoff) -> AssignmentScheduleStatus.SCHEDULED
            now.isAfter(deadline) -> AssignmentScheduleStatus.CLOSED
            else -> AssignmentScheduleStatus.OPEN
        }
    }

    private fun closeAssignmentJcodes(assignmentId: Long) {
        jCodeRepository.findByAssignmentId(assignmentId).forEach { jcode ->
            if (jcode.lifecycleStatus !in setOf(JcodeLifecycleStatus.DELETE_PENDING, JcodeLifecycleStatus.ARCHIVED)) {
                jcode.lifecycleStatus = JcodeLifecycleStatus.DELETE_PENDING
                jcode.lastError = null
                jCodeRepository.save(jcode)
                workspaceOperationStore.enqueue(
                    WorkspaceOperationTarget.JCODE, jcode.id, WorkspaceOperationAction.DELETE_JCODE
                )
            }
        }
    }

    fun toDto(assignment: Assignment): AssignmentDto {
        val starter = starterArtifactRepository.findTopByAssignmentIdAndStatusOrderByVersionDesc(
            assignment.id, StarterArtifactStatus.READY
        )
        return AssignmentDto(
            assignmentId = assignment.id,
            assignmentName = assignment.name,
            assignmentDescription = assignment.description,
            dirName = assignment.workspaceKey,
            workspaceKey = assignment.workspaceKey,
            hasStarterCode = assignment.hasStarterCode,
            lifecycleStatus = assignment.lifecycleStatus,
            scheduleStatus = assignment.scheduleStatus,
            lastError = assignment.lastError,
            starterVersion = starter?.version,
            starterChecksum = starter?.checksum,
            starterOverwritePolicy = starter?.overwritePolicy,
            archiveRetentionDays = assignment.archiveRetentionDays,
            archivedAt = assignment.archivedAt?.toString(),
            finalizedAt = assignment.finalizedAt?.toString(),
            kickoffDate = assignment.kickoffDate,
            deadlineDate = assignment.deadlineDate,
            createdAt = assignment.createdAt.toString(),
            updatedAt = assignment.updatedAt.toString()
        )
    }

    @Transactional
    fun createAssignment(courseId: Long, dto: AssignmentDto, email: String, token: String): AssignmentDto {
        validateAssignmentAuthority(courseId, email)
        val course = getActiveCourse(courseId)
        validateDates(dto.kickoffDate, dto.deadlineDate)
        if (!dto.deadlineDate.isAfter(LocalDateTime.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "새 과제의 마감 시각은 현재보다 뒤여야 합니다.")
        }
        if (assignmentRepository.existsByCourseIdAndName(course.id, dto.assignmentName)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Assignment already exists")
        }
        val assignment = assignmentRepository.saveAndFlush(
            Assignment(
                name = dto.assignmentName.trim(),
                description = dto.assignmentDescription,
                kickoffDate = dto.kickoffDate,
                deadlineDate = dto.deadlineDate,
                scheduleStatus = scheduleStatus(dto.kickoffDate, dto.deadlineDate),
                archiveRetentionDays = dto.archiveRetentionDays.coerceIn(1, 3650),
                course = course
            )
        )
        assignment.workspaceKey = "assignment-${assignment.id}"
        assignment.dirName = assignment.workspaceKey
        assignmentRepository.save(assignment)
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.ASSIGNMENT, assignment.id, WorkspaceOperationAction.PROVISION_ASSIGNMENT
        )
        return toDto(assignment)
    }

    @Transactional
    fun updateAssignment(courseId: Long, assignmentId: Long, dto: AssignmentDto, email: String): AssignmentDto {
        validateAssignmentAuthority(courseId, email)
        getActiveCourse(courseId)
        val assignment = getAssignment(courseId, assignmentId)
        if (assignment.lifecycleStatus in setOf(AssignmentLifecycleStatus.DELETING, AssignmentLifecycleStatus.ARCHIVED)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "삭제 중이거나 보관된 과제는 수정할 수 없습니다.")
        }
        validateDates(dto.kickoffDate, dto.deadlineDate)
        val requestedSchedule = scheduleStatus(dto.kickoffDate, dto.deadlineDate)
        if (assignment.scheduleStatus == AssignmentScheduleStatus.CLOSED && requestedSchedule != AssignmentScheduleStatus.CLOSED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "마감된 과제는 다시 열기 기능으로 연장해야 합니다.")
        }
        val updated = assignment.copy(
            name = dto.assignmentName.trim(),
            description = dto.assignmentDescription,
            kickoffDate = dto.kickoffDate,
            deadlineDate = dto.deadlineDate,
            scheduleStatus = requestedSchedule,
            archiveRetentionDays = dto.archiveRetentionDays.coerceIn(1, 3650),
            updatedAt = LocalDateTime.now()
        )
        val saved = assignmentRepository.save(updated)
        if (assignment.scheduleStatus != AssignmentScheduleStatus.CLOSED && requestedSchedule == AssignmentScheduleStatus.CLOSED) {
            closeAssignmentJcodes(saved.id)
            workspaceOperationStore.enqueue(
                WorkspaceOperationTarget.ASSIGNMENT, saved.id, WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION
            )
        }
        return toDto(saved)
    }

    fun uploadStarterCode(
        courseId: Long,
        assignmentId: Long,
        file: MultipartFile,
        overwritePolicy: StarterOverwritePolicy,
        deployNow: Boolean,
        email: String,
        token: String
    ): AssignmentDto {
        validateAssignmentAuthority(courseId, email)
        val course = getActiveCourse(courseId)
        val assignment = getAssignment(courseId, assignmentId)
        if (assignment.lifecycleStatus != AssignmentLifecycleStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE 상태의 과제에만 스타터 코드를 올릴 수 있습니다.")
        }
        if (assignment.scheduleStatus in setOf(AssignmentScheduleStatus.CLOSED, AssignmentScheduleStatus.ARCHIVED)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "마감되거나 보관된 과제에는 스타터 코드를 배포할 수 없습니다.")
        }
        val version = (starterArtifactRepository.findTopByAssignmentIdOrderByVersionDesc(assignment.id)?.version ?: 0) + 1
        val artifact = starterArtifactRepository.save(
            StarterArtifact(
                assignment = assignment,
                version = version,
                artifactKey = "assignments/${assignment.id}/starter/v$version.zip",
                overwritePolicy = overwritePolicy
            )
        )
        try {
            val result = generatorWebClient.post()
                .uri("/api/workspace/assignments/starter/upload")
                .attribute(GENERATOR_SCOPE_ATTRIBUTE, "workspace:write")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(LinkedMultiValueMap<String, Any>().apply {
                    add("course_id", course.id)
                    add("namespace", "jcode-${course.code.lowercase()}-${course.clss}")
                    add("assignment_id", assignment.id)
                    add("version", version)
                    add("artifact_key", artifact.artifactKey)
                    add("file", object : ByteArrayResource(file.bytes) {
                        override fun getFilename(): String = file.originalFilename ?: "starter.zip"
                    })
                })
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() ?: throw IllegalStateException("Generator 응답이 없습니다.")
            artifact.checksum = result["checksum"] as? String
                ?: throw IllegalStateException("Generator가 checksum을 반환하지 않았습니다.")
            artifact.sizeBytes = (result["size_bytes"] as? Number)?.toLong() ?: file.size
            artifact.status = StarterArtifactStatus.READY
            starterArtifactRepository.save(artifact)
            assignment.hasStarterCode = true
            assignmentRepository.save(assignment)
        } catch (error: Exception) {
            artifact.status = StarterArtifactStatus.FAILED
            artifact.lastError = (error.message ?: error.javaClass.simpleName).take(4000)
            starterArtifactRepository.save(artifact)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "스타터 코드 원본 저장에 실패했습니다: ${error.message}")
        }
        if (deployNow) {
            workspaceOperationStore.enqueue(
                WorkspaceOperationTarget.ASSIGNMENT,
                assignment.id,
                WorkspaceOperationAction.DISTRIBUTE_STARTER,
                artifact.id
            )
        }
        return toDto(assignment)
    }

    @Transactional
    fun deleteAssignment(courseId: Long, assignmentId: Long, email: String, retentionDays: Int = 90) {
        validateAssignmentAuthority(courseId, email)
        getActiveCourse(courseId)
        val assignment = getAssignment(courseId, assignmentId)
        if (assignment.lifecycleStatus == AssignmentLifecycleStatus.ARCHIVED) return
        if (assignment.lifecycleStatus == AssignmentLifecycleStatus.DELETING) return
        assignment.lifecycleStatus = AssignmentLifecycleStatus.DELETING
        assignment.archiveRetentionDays = retentionDays.coerceIn(1, 3650)
        assignment.lastError = null
        assignmentRepository.save(assignment)
        closeAssignmentJcodes(assignment.id)
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.ASSIGNMENT, assignment.id, WorkspaceOperationAction.ARCHIVE_ASSIGNMENT
        )
    }

    @Transactional
    fun reopenAssignment(courseId: Long, assignmentId: Long, newDeadline: LocalDateTime, email: String): AssignmentDto {
        validateAssignmentAuthority(courseId, email)
        getActiveCourse(courseId)
        val assignment = getAssignment(courseId, assignmentId)
        if (assignment.lifecycleStatus != AssignmentLifecycleStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE 상태의 과제만 다시 열 수 있습니다.")
        }
        if (!newDeadline.isAfter(LocalDateTime.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "새 마감 시각은 현재보다 뒤여야 합니다.")
        }
        if (assignment.scheduleStatus != AssignmentScheduleStatus.CLOSED || assignment.finalizedAt == null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "최종 작업물 보관이 완료된 과제만 다시 열 수 있습니다.")
        }
        if (assignment.finalizedAt!!.plusDays(assignment.archiveRetentionDays.toLong()).isBefore(LocalDateTime.now())) {
            throw ResponseStatusException(HttpStatus.GONE, "최종 작업물 보관 기간이 지나 과제를 다시 열 수 없습니다.")
        }
        val updated = assignment.copy(
            deadlineDate = newDeadline,
            scheduleStatus = AssignmentScheduleStatus.OPEN,
            lifecycleStatus = AssignmentLifecycleStatus.PROVISIONING,
            updatedAt = LocalDateTime.now()
        )
        val saved = assignmentRepository.save(updated)
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.ASSIGNMENT, saved.id, WorkspaceOperationAction.RESTORE_ASSIGNMENT
        )
        return toDto(saved)
    }

    fun retryAssignment(courseId: Long, assignmentId: Long, email: String) {
        validateAssignmentAuthority(courseId, email)
        val assignment = getAssignment(courseId, assignmentId)
        if (assignment.lifecycleStatus != AssignmentLifecycleStatus.PROVISION_FAILED && assignment.lastError.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "현재 과제에 실패한 작업이 없습니다.")
        }
        if (!workspaceOperationStore.retry(WorkspaceOperationTarget.ASSIGNMENT, assignmentId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "재시도할 실패 작업이 없습니다.")
        }
    }

    @Scheduled(fixedDelayString = "\${assignment.schedule.refresh-ms:30000}")
    @Transactional
    fun refreshScheduleStatuses() {
        val now = LocalDateTime.now()
        assignmentRepository.findAll().forEach { assignment ->
            if (assignment.lifecycleStatus in setOf(AssignmentLifecycleStatus.DELETING, AssignmentLifecycleStatus.ARCHIVED)) return@forEach
            val expected = when {
                now.isBefore(assignment.kickoffDate) -> AssignmentScheduleStatus.SCHEDULED
                now.isAfter(assignment.deadlineDate) -> AssignmentScheduleStatus.CLOSED
                else -> AssignmentScheduleStatus.OPEN
            }
            if (assignment.scheduleStatus != expected) {
                assignment.scheduleStatus = expected
                assignment.updatedAt = now
                assignmentRepository.save(assignment)
                if (expected == AssignmentScheduleStatus.CLOSED) {
                    closeAssignmentJcodes(assignment.id)
                    workspaceOperationStore.enqueue(
                        WorkspaceOperationTarget.ASSIGNMENT,
                        assignment.id,
                        WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION
                    )
                }
            }
        }
    }
}
