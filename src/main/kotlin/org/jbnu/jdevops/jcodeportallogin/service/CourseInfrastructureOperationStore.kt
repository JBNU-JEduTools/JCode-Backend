package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureAction
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureOperation
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureOperationStatus
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.repo.CourseInfrastructureOperationRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.JCodeRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.math.min

data class ClaimedCourseInfrastructureOperation(
    val id: Long,
    val courseId: Long,
    val action: CourseInfrastructureAction,
    val idempotencyKey: String,
    val attempts: Int
)

@Service
class CourseInfrastructureOperationStore(
    private val operationRepository: CourseInfrastructureOperationRepository,
    private val courseRepository: CourseRepository,
    private val jCodeRepository: JCodeRepository,
    @Value("\${course.lifecycle.max-attempts:10}") private val maxAttempts: Int,
    @Value("\${course.lifecycle.lock-timeout-seconds:300}") private val lockTimeoutSeconds: Long
) {
    private val activeStatuses = setOf(
        CourseInfrastructureOperationStatus.PENDING,
        CourseInfrastructureOperationStatus.PROCESSING
    )

    @Transactional
    fun enqueue(courseId: Long, action: CourseInfrastructureAction) {
        if (operationRepository.existsByCourseIdAndStatusIn(courseId, activeStatuses)) {
            return
        }
        operationRepository.save(CourseInfrastructureOperation(courseId = courseId, action = action))
    }

    @Transactional
    fun claimNext(): ClaimedCourseInfrastructureOperation? {
        val now = LocalDateTime.now()
        val operation = operationRepository.findReadyForUpdate(
            now = now,
            staleBefore = now.minusSeconds(lockTimeoutSeconds),
            pending = CourseInfrastructureOperationStatus.PENDING,
            processing = CourseInfrastructureOperationStatus.PROCESSING,
            pageable = PageRequest.of(0, 1)
        ).firstOrNull() ?: return null

        operation.status = CourseInfrastructureOperationStatus.PROCESSING
        operation.lockedAt = now
        operation.attempts += 1
        operation.updatedAt = now
        operationRepository.save(operation)
        return ClaimedCourseInfrastructureOperation(
            id = operation.id,
            courseId = operation.courseId,
            action = operation.action,
            idempotencyKey = operation.idempotencyKey,
            attempts = operation.attempts
        )
    }

    @Transactional
    fun loadCourse(operation: ClaimedCourseInfrastructureOperation) =
        courseRepository.findById(operation.courseId).orElse(null)

    @Transactional
    fun markSucceeded(operationId: Long) {
        val operation = operationRepository.findById(operationId).orElseThrow()
        val course = courseRepository.findById(operation.courseId).orElse(null)
        if (course != null) {
            when (operation.action) {
                CourseInfrastructureAction.PROVISION_NAMESPACE -> {
                    course.status = CourseStatus.ACTIVE
                    course.endedAt = null
                }
                CourseInfrastructureAction.DELETE_WORKLOADS -> {
                    jCodeRepository.deleteAll(jCodeRepository.findByCourseId(course.id))
                    course.status = CourseStatus.ENDED
                    course.endedAt = LocalDateTime.now()
                }
                CourseInfrastructureAction.DELETE_NAMESPACE -> course.status = CourseStatus.ARCHIVED
            }
            courseRepository.save(course)
        }
        operation.status = CourseInfrastructureOperationStatus.SUCCEEDED
        operation.lockedAt = null
        operation.lastError = null
        operation.updatedAt = LocalDateTime.now()
        operationRepository.save(operation)
    }

    @Transactional
    fun markFailed(operationId: Long, error: Throwable) {
        val operation = operationRepository.findById(operationId).orElseThrow()
        val now = LocalDateTime.now()
        operation.lastError = (error.message ?: error.javaClass.simpleName).take(4000)
        operation.lockedAt = null
        operation.updatedAt = now
        if (operation.attempts >= maxAttempts) {
            operation.status = CourseInfrastructureOperationStatus.FAILED
        } else {
            operation.status = CourseInfrastructureOperationStatus.PENDING
            val delaySeconds = min(300L, 1L shl min(operation.attempts, 8))
            operation.nextAttemptAt = now.plusSeconds(delaySeconds)
        }
        operationRepository.save(operation)

        courseRepository.findById(operation.courseId).ifPresent { course ->
            course.status = CourseStatus.ERROR
            courseRepository.save(course)
        }
    }

    @Transactional
    fun retryFailed(courseId: Long): Boolean {
        if (operationRepository.existsByCourseIdAndStatusIn(courseId, activeStatuses)) {
            return false
        }
        val operation = operationRepository.findTopByCourseIdAndStatusOrderByCreatedAtDesc(
            courseId,
            CourseInfrastructureOperationStatus.FAILED
        ) ?: return false
        val course = courseRepository.findById(courseId).orElse(null) ?: return false

        course.status = when (operation.action) {
            CourseInfrastructureAction.PROVISION_NAMESPACE -> CourseStatus.PROVISIONING
            CourseInfrastructureAction.DELETE_WORKLOADS -> CourseStatus.TERMINATING
            CourseInfrastructureAction.DELETE_NAMESPACE -> CourseStatus.ARCHIVING
        }
        courseRepository.save(course)

        val now = LocalDateTime.now()
        operation.status = CourseInfrastructureOperationStatus.PENDING
        operation.attempts = 0
        operation.nextAttemptAt = now
        operation.lockedAt = null
        operation.lastError = null
        operation.updatedAt = now
        operationRepository.save(operation)
        return true
    }
}
