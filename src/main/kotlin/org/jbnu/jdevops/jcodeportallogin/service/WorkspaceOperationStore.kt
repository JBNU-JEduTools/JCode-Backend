package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.*
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.StarterArtifactRepository
import org.jbnu.jdevops.jcodeportallogin.repo.WorkspaceOperationRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.JCodeRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.math.min

data class ClaimedWorkspaceOperation(
    val id: Long,
    val targetType: WorkspaceOperationTarget,
    val targetId: Long,
    val action: WorkspaceOperationAction,
    val artifactId: Long?,
    val idempotencyKey: String,
    val attempts: Int
)

@Service
class WorkspaceOperationStore(
    private val operationRepository: WorkspaceOperationRepository,
    private val assignmentRepository: AssignmentRepository,
    private val artifactRepository: StarterArtifactRepository,
    private val userCoursesRepository: UserCoursesRepository,
    private val jCodeRepository: JCodeRepository,
    @Value("\${workspace.lifecycle.max-attempts:10}") private val maxAttempts: Int,
    @Value("\${workspace.lifecycle.lock-timeout-seconds:300}") private val lockTimeoutSeconds: Long
) {
    private val active = setOf(WorkspaceOperationStatus.PENDING, WorkspaceOperationStatus.PROCESSING)

    @Transactional
    fun enqueue(targetType: WorkspaceOperationTarget, targetId: Long, action: WorkspaceOperationAction, artifactId: Long? = null) {
        operationRepository.save(
            WorkspaceOperation(targetType = targetType, targetId = targetId, action = action, artifactId = artifactId)
        )
    }

    @Transactional
    fun claimNext(): ClaimedWorkspaceOperation? {
        val now = LocalDateTime.now()
        val operation = operationRepository.findReadyForUpdate(
            now,
            now.minusSeconds(lockTimeoutSeconds),
            WorkspaceOperationStatus.PENDING,
            WorkspaceOperationStatus.PROCESSING,
            PageRequest.of(0, 1)
        ).firstOrNull() ?: return null
        operation.status = WorkspaceOperationStatus.PROCESSING
        operation.lockedAt = now
        operation.attempts += 1
        operation.updatedAt = now
        operationRepository.save(operation)
        return ClaimedWorkspaceOperation(
            operation.id, operation.targetType, operation.targetId, operation.action,
            operation.artifactId, operation.idempotencyKey, operation.attempts
        )
    }

    @Transactional(readOnly = true)
    fun loadAssignment(id: Long) = assignmentRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun loadArtifact(id: Long) = artifactRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun loadLatestAssignmentArtifact(assignmentId: Long) =
        artifactRepository.findTopByAssignmentIdAndStatusOrderByVersionDesc(assignmentId, StarterArtifactStatus.READY)

    @Transactional(readOnly = true)
    fun loadMembership(id: Long) = userCoursesRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun loadJcode(id: Long) = jCodeRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun loadLatestCourseArtifacts(courseId: Long): List<StarterArtifact> =
        artifactRepository.findByAssignmentCourseIdAndStatusOrderByVersionDesc(courseId, StarterArtifactStatus.READY)
            .filter {
                it.assignment.lifecycleStatus == AssignmentLifecycleStatus.ACTIVE &&
                    it.assignment.scheduleStatus in setOf(AssignmentScheduleStatus.SCHEDULED, AssignmentScheduleStatus.OPEN)
            }
            .distinctBy { it.assignment.id }

    @Transactional(readOnly = true)
    fun loadMembershipJcodes(membershipId: Long): List<Pair<String, String>> {
        val membership = userCoursesRepository.findById(membershipId).orElse(null) ?: return emptyList()
        return jCodeRepository.findAllByUserCourse(membership).map { it.deploymentName to it.serviceName }
    }

    @Transactional(readOnly = true)
    fun loadAssignmentJcodes(assignmentId: Long): List<Pair<String, String>> =
        jCodeRepository.findByAssignmentId(assignmentId)
            .filter { it.lifecycleStatus != JcodeLifecycleStatus.ARCHIVED }
            .map { it.deploymentName to it.serviceName }

    @Transactional(readOnly = true)
    fun loadActiveAssignmentKeys(courseId: Long): List<String> = assignmentRepository.findByCourseId(courseId)
        .filter {
            it.lifecycleStatus == AssignmentLifecycleStatus.ACTIVE &&
                it.scheduleStatus in setOf(AssignmentScheduleStatus.SCHEDULED, AssignmentScheduleStatus.OPEN)
        }
        .map { it.workspaceKey }

    @Transactional
    fun markSucceeded(operation: ClaimedWorkspaceOperation, result: Map<*, *>? = null) {
        val stored = operationRepository.findById(operation.id).orElseThrow()
        if (result?.get("_skipStateUpdate") != true) when (operation.action) {
            WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH,
            WorkspaceOperationAction.PROVISION_ASSIGNMENT -> assignmentRepository.findById(operation.targetId).ifPresent {
                if (it.lifecycleStatus == AssignmentLifecycleStatus.PROVISIONING) {
                    it.lifecycleStatus = AssignmentLifecycleStatus.ACTIVE
                    it.lastError = null
                    it.updatedAt = LocalDateTime.now()
                    assignmentRepository.save(it)
                }
            }
            WorkspaceOperationAction.RESTORE_ASSIGNMENT -> assignmentRepository.findById(operation.targetId).ifPresent {
                it.lifecycleStatus = AssignmentLifecycleStatus.ACTIVE
                it.finalizedAt = null
                it.lastError = null
                it.updatedAt = LocalDateTime.now()
                assignmentRepository.save(it)
            }
            WorkspaceOperationAction.DISTRIBUTE_STARTER -> {
                operation.artifactId?.let { id -> artifactRepository.findById(id).ifPresent { artifact ->
                    artifact.status = StarterArtifactStatus.READY
                    artifact.lastError = null
                    artifactRepository.save(artifact)
                    artifact.assignment.hasStarterCode = true
                    artifact.assignment.lastError = null
                    assignmentRepository.save(artifact.assignment)
                } }
            }
            WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION -> assignmentRepository.findById(operation.targetId).ifPresent {
                if (it.lifecycleStatus == AssignmentLifecycleStatus.ACTIVE && it.scheduleStatus == AssignmentScheduleStatus.CLOSED) {
                    it.finalizedAt = LocalDateTime.now()
                    it.lastError = null
                    assignmentRepository.save(it)
                }
            }
            WorkspaceOperationAction.ARCHIVE_ASSIGNMENT -> assignmentRepository.findById(operation.targetId).ifPresent {
                it.lifecycleStatus = AssignmentLifecycleStatus.ARCHIVED
                it.scheduleStatus = AssignmentScheduleStatus.ARCHIVED
                it.archivedAt = LocalDateTime.now()
                it.lastError = null
                assignmentRepository.save(it)
            }
            WorkspaceOperationAction.PROVISION_MEMBERSHIP -> userCoursesRepository.findById(operation.targetId).ifPresent {
                if (it.lifecycleStatus == MembershipStatus.PROVISIONING) {
                    it.lifecycleStatus = MembershipStatus.READY
                    it.lastError = null
                    userCoursesRepository.save(it)
                }
            }
            WorkspaceOperationAction.DELETE_MEMBERSHIP -> userCoursesRepository.findById(operation.targetId).ifPresent {
                it.lifecycleStatus = MembershipStatus.ARCHIVED
                it.archivedAt = LocalDateTime.now()
                it.lastError = null
                it.jcodes.forEach { jcode ->
                    jcode.lifecycleStatus = JcodeLifecycleStatus.ARCHIVED
                    jcode.archivedAt = LocalDateTime.now()
                    jCodeRepository.save(jcode)
                }
                userCoursesRepository.save(it)
            }
            WorkspaceOperationAction.PROVISION_JCODE -> jCodeRepository.findById(operation.targetId).ifPresent {
                if (it.lifecycleStatus == JcodeLifecycleStatus.PROVISIONING && it.userCourse.lifecycleStatus == MembershipStatus.READY) {
                    it.jcodeUrl = result?.get("jcodeUrl") as? String
                        ?: throw IllegalStateException("Generator가 jcodeUrl을 반환하지 않았습니다.")
                    it.lifecycleStatus = JcodeLifecycleStatus.READY
                    it.lastError = null
                    jCodeRepository.save(it)
                } else {
                    if (it.lifecycleStatus != JcodeLifecycleStatus.ARCHIVED) {
                        it.lifecycleStatus = JcodeLifecycleStatus.DELETE_PENDING
                        jCodeRepository.save(it)
                    }
                    operationRepository.save(
                        WorkspaceOperation(
                            targetType = WorkspaceOperationTarget.JCODE,
                            targetId = it.id,
                            action = WorkspaceOperationAction.DELETE_JCODE
                        )
                    )
                }
            }
            WorkspaceOperationAction.DELETE_JCODE -> jCodeRepository.findById(operation.targetId).ifPresent {
                it.lifecycleStatus = JcodeLifecycleStatus.ARCHIVED
                it.archivedAt = LocalDateTime.now()
                it.lastError = null
                jCodeRepository.save(it)
            }
        }
        stored.status = WorkspaceOperationStatus.SUCCEEDED
        stored.lockedAt = null
        stored.lastError = null
        stored.updatedAt = LocalDateTime.now()
        operationRepository.save(stored)
    }

    @Transactional
    fun markFailed(operation: ClaimedWorkspaceOperation, error: Throwable) {
        val stored = operationRepository.findById(operation.id).orElseThrow()
        val now = LocalDateTime.now()
        val message = (error.message ?: error.javaClass.simpleName).take(4000)
        stored.lastError = message
        stored.lockedAt = null
        stored.updatedAt = now
        if (stored.attempts >= maxAttempts) {
            stored.status = WorkspaceOperationStatus.FAILED
            when (operation.targetType) {
                WorkspaceOperationTarget.ASSIGNMENT -> assignmentRepository.findById(operation.targetId).ifPresent { assignment ->
                    if (operation.action in setOf(
                            WorkspaceOperationAction.PROVISION_ASSIGNMENT,
                            WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH,
                            WorkspaceOperationAction.RESTORE_ASSIGNMENT
                        )) {
                        assignment.lifecycleStatus = AssignmentLifecycleStatus.PROVISION_FAILED
                    }
                    assignment.lastError = message
                    assignmentRepository.save(assignment)
                }
                WorkspaceOperationTarget.MEMBERSHIP -> userCoursesRepository.findById(operation.targetId).ifPresent {
                    it.lifecycleStatus = MembershipStatus.DELETE_FAILED.takeIf {
                        operation.action == WorkspaceOperationAction.DELETE_MEMBERSHIP
                    } ?: MembershipStatus.PROVISION_FAILED
                    it.lastError = message
                    userCoursesRepository.save(it)
                }
                WorkspaceOperationTarget.JCODE -> jCodeRepository.findById(operation.targetId).ifPresent {
                    it.lifecycleStatus = if (operation.action == WorkspaceOperationAction.DELETE_JCODE) {
                        JcodeLifecycleStatus.DELETE_FAILED
                    } else JcodeLifecycleStatus.PROVISION_FAILED
                    it.lastError = message
                    jCodeRepository.save(it)
                }
            }
            operation.artifactId?.let { id -> artifactRepository.findById(id).ifPresent { artifact ->
                artifact.status = StarterArtifactStatus.FAILED
                artifact.lastError = message
                artifactRepository.save(artifact)
            } }
        } else {
            stored.status = WorkspaceOperationStatus.PENDING
            stored.nextAttemptAt = now.plusSeconds(min(300L, 1L shl min(stored.attempts, 8)))
        }
        operationRepository.save(stored)
    }

    @Transactional
    fun retry(targetType: WorkspaceOperationTarget, targetId: Long): Boolean {
        if (operationRepository.existsByTargetTypeAndTargetIdAndStatusIn(targetType, targetId, active)) return false
        val operation = operationRepository.findTopByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
            targetType, targetId, WorkspaceOperationStatus.FAILED
        ) ?: return false
        operation.status = WorkspaceOperationStatus.PENDING
        operation.attempts = 0
        operation.nextAttemptAt = LocalDateTime.now()
        operation.lockedAt = null
        operation.lastError = null
        operationRepository.save(operation)
        if (targetType == WorkspaceOperationTarget.ASSIGNMENT) {
            assignmentRepository.findById(targetId).ifPresent {
                if (operation.action in setOf(
                        WorkspaceOperationAction.PROVISION_ASSIGNMENT,
                        WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH,
                        WorkspaceOperationAction.RESTORE_ASSIGNMENT
                    )) {
                    it.lifecycleStatus = AssignmentLifecycleStatus.PROVISIONING
                }
                it.lastError = null
                assignmentRepository.save(it)
            }
        } else if (targetType == WorkspaceOperationTarget.MEMBERSHIP) {
            userCoursesRepository.findById(targetId).ifPresent {
                it.lifecycleStatus = if (operation.action == WorkspaceOperationAction.DELETE_MEMBERSHIP) {
                    MembershipStatus.DELETE_PENDING
                } else MembershipStatus.PROVISIONING
                it.lastError = null
                userCoursesRepository.save(it)
            }
        } else {
            jCodeRepository.findById(targetId).ifPresent {
                it.lifecycleStatus = if (operation.action == WorkspaceOperationAction.DELETE_JCODE) {
                    JcodeLifecycleStatus.DELETE_PENDING
                } else JcodeLifecycleStatus.PROVISIONING
                it.lastError = null
                jCodeRepository.save(it)
            }
        }
        return true
    }
}
