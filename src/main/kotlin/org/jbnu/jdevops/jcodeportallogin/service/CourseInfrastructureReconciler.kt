package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.config.GENERATOR_SCOPE_ATTRIBUTE
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureAction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

@Component
class CourseInfrastructureReconciler(
    private val operationStore: CourseInfrastructureOperationStore,
    @Qualifier("generatorBootstrapWebClient") private val bootstrapClient: WebClient,
    @Qualifier("generatorWorkspaceWebClient") private val workspaceClient: WebClient,
    @Value("\${course.lifecycle.batch-size:10}") private val batchSize: Int,
    @Value("\${course.lifecycle.request-timeout-seconds:90}") private val requestTimeoutSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${course.lifecycle.reconcile-delay-ms:5000}",
        initialDelayString = "\${course.lifecycle.initial-delay-ms:5000}"
    )
    fun reconcile() {
        repeat(batchSize) {
            val operation = operationStore.claimNext() ?: return
            val course = operationStore.loadCourse(operation)
            if (course == null) {
                operationStore.markSucceeded(operation.id)
                return@repeat
            }
            try {
                execute(operation, course)
                operationStore.markSucceeded(operation.id)
            } catch (ex: Exception) {
                logger.error(
                    "Course infrastructure reconcile failed: operation={}, course={}, attempt={}",
                    operation.action,
                    operation.courseId,
                    operation.attempts,
                    ex
                )
                operationStore.markFailed(operation.id, ex)
            }
        }
    }

    private fun execute(operation: ClaimedCourseInfrastructureOperation, course: Course) {
        val namespace = "jcode-${course.code.lowercase()}-${course.clss}"
        when (operation.action) {
            CourseInfrastructureAction.PROVISION_NAMESPACE -> bootstrapClient.post()
                .uri("/api/namespace")
                .attribute(GENERATOR_SCOPE_ATTRIBUTE, "namespace:write")
                .header("Idempotency-Key", operation.idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf(
                    "course_id" to course.id,
                    "namespace" to namespace,
                    "environment_profile" to course.environmentProfile.name,
                    "use_vnc" to course.useVnc,
                    "use_jupyter" to course.useJupyter,
                    "base_image" to course.baseImage,
                    "resource_profile" to course.resourceProfile.name,
                    "egress_policy" to course.egressPolicy.name,
                    "workspace_scope" to course.workspaceScope.name
                ))
                .retrieve()
                .bodyToMono(Map::class.java)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .block()

            CourseInfrastructureAction.DELETE_WORKLOADS -> delete(
                workspaceClient,
                "/api/namespace/$namespace/resources?course_id=${course.id}",
                "namespace:resources:delete",
                operation.idempotencyKey
            )

            CourseInfrastructureAction.DELETE_NAMESPACE -> delete(
                bootstrapClient,
                "/api/namespace/$namespace?course_id=${course.id}",
                "namespace:delete",
                operation.idempotencyKey,
                requireDeletedConfirmation = true
            )
        }
    }

    private fun delete(
        client: WebClient,
        uri: String,
        scope: String,
        idempotencyKey: String,
        requireDeletedConfirmation: Boolean = false
    ) {
        try {
            val response = client.delete()
                .uri(uri)
                .attribute(GENERATOR_SCOPE_ATTRIBUTE, scope)
                .header("Idempotency-Key", idempotencyKey)
                .retrieve()
                .bodyToMono(Map::class.java)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .block()
            if (requireDeletedConfirmation && response?.get("deleted") != true) {
                throw IllegalStateException("Generator가 Namespace 실제 삭제 완료를 확인하지 않았습니다.")
            }
        } catch (_: WebClientResponseException.NotFound) {
            // Desired state is already reached.
        }
    }
}
