package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.config.GENERATOR_SCOPE_ATTRIBUTE
import org.jbnu.jdevops.jcodeportallogin.entity.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class WorkspaceReconciler(
    private val operationStore: WorkspaceOperationStore,
    @Qualifier("generatorWorkspaceWebClient") private val generator: WebClient,
    @Value("\${workspace.lifecycle.batch-size:20}") private val batchSize: Int,
    @Value("\${workspace.lifecycle.request-timeout-seconds:90}") private val timeoutSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val skipped = mapOf("_skipStateUpdate" to true)

    @Scheduled(
        fixedDelayString = "\${workspace.lifecycle.reconcile-delay-ms:3000}",
        initialDelayString = "\${workspace.lifecycle.initial-delay-ms:10000}"
    )
    fun reconcile() {
        repeat(batchSize) {
            val operation = operationStore.claimNext() ?: return
            try {
                val result = execute(operation)
                operationStore.markSucceeded(operation, result)
            } catch (error: Exception) {
                logger.error(
                    "Workspace reconcile failed: target={}/{}, action={}, attempt={}",
                    operation.targetType, operation.targetId, operation.action, operation.attempts, error
                )
                operationStore.markFailed(operation, error)
            }
        }
    }

    private fun execute(operation: ClaimedWorkspaceOperation): Map<*, *>? =
        when (operation.targetType) {
            WorkspaceOperationTarget.ASSIGNMENT -> executeAssignment(operation)
            WorkspaceOperationTarget.MEMBERSHIP -> executeMembership(operation)
            WorkspaceOperationTarget.JCODE -> executeJcode(operation)
        }

    private fun executeAssignment(operation: ClaimedWorkspaceOperation): Map<*, *>? {
        val assignment = operationStore.loadAssignment(operation.targetId) ?: return null
        val course = assignment.course
        val namespace = "jcode-${course.code.lowercase()}-${course.clss}"
        when (operation.action) {
            WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH,
            WorkspaceOperationAction.PROVISION_ASSIGNMENT -> {
                if (assignment.lifecycleStatus != AssignmentLifecycleStatus.PROVISIONING) {
                    return skipped
                }
                return post(
                "/api/workspace/assignments/provision",
                "workspace:write",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "workspace_key" to assignment.workspaceKey,
                    "legacy_dir_name" to assignment.legacyDirName
                )
                )
            }
            WorkspaceOperationAction.DISTRIBUTE_STARTER -> {
                if (assignment.lifecycleStatus != AssignmentLifecycleStatus.ACTIVE ||
                    assignment.scheduleStatus !in setOf(
                        AssignmentScheduleStatus.SCHEDULED,
                        AssignmentScheduleStatus.OPEN
                    )) return skipped
                val artifact = operation.artifactId?.let(operationStore::loadArtifact)
                    ?: throw IllegalStateException("스타터 artifact를 찾을 수 없습니다.")
                return post(
                    "/api/workspace/assignments/starter/distribute",
                    "workspace:write",
                    operation.idempotencyKey,
                    mapOf(
                        "course_id" to course.id,
                        "namespace" to namespace,
                        "workspace_key" to assignment.workspaceKey,
                        "artifact_key" to artifact.artifactKey,
                        "checksum" to artifact.checksum,
                        "overwrite_policy" to artifact.overwritePolicy.name
                    )
                )
            }
            WorkspaceOperationAction.ARCHIVE_ASSIGNMENT -> {
                if (assignment.lifecycleStatus != AssignmentLifecycleStatus.DELETING) {
                    return skipped
                }
                val jcodes = operationStore.loadAssignmentJcodes(assignment.id)
                return post(
                "/api/workspace/assignments/archive",
                "workspace:write",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "workspace_key" to assignment.workspaceKey,
                    "retention_days" to assignment.archiveRetentionDays,
                    "deployments" to jcodes.map { it.first },
                    "services" to jcodes.map { it.second }
                )
                )
            }
            WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION -> {
                if (assignment.lifecycleStatus != AssignmentLifecycleStatus.ACTIVE ||
                    assignment.scheduleStatus != AssignmentScheduleStatus.CLOSED ||
                    assignment.finalizedAt != null) return skipped
                val jcodes = operationStore.loadAssignmentJcodes(assignment.id)
                return post(
                "/api/workspace/assignments/finalize",
                "workspace:write",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "workspace_key" to assignment.workspaceKey,
                    "retention_days" to assignment.archiveRetentionDays,
                    "deployments" to jcodes.map { it.first },
                    "services" to jcodes.map { it.second }
                )
                )
            }
            WorkspaceOperationAction.RESTORE_ASSIGNMENT -> {
                if (assignment.lifecycleStatus != AssignmentLifecycleStatus.PROVISIONING ||
                    assignment.scheduleStatus != AssignmentScheduleStatus.OPEN) return skipped
                val starter = operationStore.loadLatestAssignmentArtifact(assignment.id)
                return post(
                "/api/workspace/assignments/restore",
                "workspace:write",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "workspace_key" to assignment.workspaceKey,
                    "retention_days" to assignment.archiveRetentionDays,
                    "starter_artifact_key" to starter?.artifactKey,
                    "starter_checksum" to starter?.checksum,
                    "starter_overwrite_policy" to (starter?.overwritePolicy?.name ?: "PRESERVE_EXISTING")
                )
                )
            }
            else -> throw IllegalStateException("과제 대상에 잘못된 작업입니다: ${operation.action}")
        }
    }

    private fun executeMembership(operation: ClaimedWorkspaceOperation): Map<*, *>? {
        val membership = operationStore.loadMembership(operation.targetId) ?: return null
        val course = membership.course
        val namespace = "jcode-${course.code.lowercase()}-${course.clss}"
        val jcodeNames = operationStore.loadMembershipJcodes(membership.id)
        return when (operation.action) {
            WorkspaceOperationAction.PROVISION_MEMBERSHIP -> if (membership.lifecycleStatus == MembershipStatus.PROVISIONING) post(
                "/api/workspace/students/provision",
                "workspace:write",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "student_num" to membership.user.studentNum.toString(),
                    "workspace_keys" to operationStore.loadActiveAssignmentKeys(course.id),
                    "artifacts" to operationStore.loadLatestCourseArtifacts(course.id).map {
                        mapOf(
                            "workspace_key" to it.assignment.workspaceKey,
                            "artifact_key" to it.artifactKey,
                            "checksum" to it.checksum,
                            "overwrite_policy" to it.overwritePolicy.name
                        )
                    }
                )
            ) else skipped
            WorkspaceOperationAction.DELETE_MEMBERSHIP -> if (membership.lifecycleStatus in setOf(
                MembershipStatus.DELETE_PENDING,
                MembershipStatus.DELETE_FAILED
            )) post(
                "/api/workspace/students/archive",
                "workspace:write",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "student_num" to membership.user.studentNum.toString(),
                    "deployments" to jcodeNames.map { it.first },
                    "services" to jcodeNames.map { it.second },
                    "retention_days" to 90,
                    "archive_key" to operation.idempotencyKey
                )
            ) else skipped
            else -> throw IllegalStateException("가입 대상에 잘못된 작업입니다: ${operation.action}")
        }
    }

    private fun executeJcode(operation: ClaimedWorkspaceOperation): Map<*, *>? {
        val jcode = operationStore.loadJcode(operation.targetId) ?: return null
        val course = jcode.course
        val namespace = "jcode-${course.code.lowercase()}-${course.clss}"
        return when (operation.action) {
            WorkspaceOperationAction.PROVISION_JCODE -> if (
                jcode.lifecycleStatus == JcodeLifecycleStatus.PROVISIONING &&
                jcode.userCourse.lifecycleStatus == MembershipStatus.READY
            ) post(
                "/api/jcode",
                "jcode:write",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "deployment_name" to jcode.deploymentName,
                    "service_name" to jcode.serviceName,
                    "app_label" to jcode.deploymentName,
                    "file_path" to if (jcode.snapshot) {
                        "${course.code.lowercase()}-${course.clss}"
                    } else "workspace/${course.code.lowercase()}-${course.clss}-${jcode.user.studentNum}",
                    "student_num" to jcode.user.studentNum.toString(),
                    "use_vnc" to course.useVnc,
                    "environment_profile" to course.environmentProfile.name,
                    "use_jupyter" to course.useJupyter,
                    "base_image" to course.baseImage,
                    "resource_profile" to course.resourceProfile.name,
                    "egress_policy" to course.egressPolicy.name,
                    "workspace_scope" to course.workspaceScope.name,
                    "assignment_workspace_key" to jcode.assignment?.workspaceKey,
                    "use_snapshot" to jcode.snapshot,
                    "hw_count" to course.hwCount,
                    "prac_count" to if (course.pracEnabled) course.pracCount else 0,
                    "assignment_dirs" to if (course.workspaceScope == WorkspaceScope.COURSE) {
                        operationStore.loadActiveAssignmentKeys(course.id)
                    } else emptyList<String>()
                )
            ) else skipped
            WorkspaceOperationAction.DELETE_JCODE -> if (jcode.lifecycleStatus in setOf(
                JcodeLifecycleStatus.DELETE_PENDING,
                JcodeLifecycleStatus.DELETE_FAILED,
                JcodeLifecycleStatus.ARCHIVED
            )) delete(
                "/api/jcode",
                "jcode:delete",
                operation.idempotencyKey,
                mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "deployment_name" to jcode.deploymentName,
                    "service_name" to jcode.serviceName
                )
            ) else skipped
            else -> throw IllegalStateException("JCode 대상에 잘못된 작업입니다: ${operation.action}")
        }
    }

    private fun post(uri: String, scope: String, idempotencyKey: String, body: Map<String, Any?>): Map<*, *>? =
        generator.post()
            .uri(uri)
            .attribute(GENERATOR_SCOPE_ATTRIBUTE, scope)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .block()

    private fun delete(uri: String, scope: String, idempotencyKey: String, body: Map<String, Any?>): Map<*, *>? =
        generator.method(org.springframework.http.HttpMethod.DELETE)
            .uri(uri)
            .attribute(GENERATOR_SCOPE_ATTRIBUTE, scope)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .block()
}
