package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureAction
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureOperation
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureOperationStatus
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.repo.CourseInfrastructureOperationRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class CourseInfrastructureOperationStoreTest {
    private val operationRepository = mock(CourseInfrastructureOperationRepository::class.java)
    private val courseRepository = mock(CourseRepository::class.java)
    private val store = CourseInfrastructureOperationStore(operationRepository, courseRepository, 3, 300)

    @Test
    fun `transient infrastructure failure keeps course provisioning`() {
        val course = course()
        val operation = operation(attempts = 1)
        `when`(operationRepository.findById(operation.id)).thenReturn(Optional.of(operation))

        store.markFailed(operation.id, RuntimeException("internal infrastructure detail"))

        assertEquals(CourseInfrastructureOperationStatus.PENDING, operation.status)
        assertEquals(CourseStatus.PROVISIONING, course.status)
        verify(courseRepository, never()).save(course)
    }

    @Test
    fun `terminal infrastructure failure exposes only error state`() {
        val course = course()
        val operation = operation(attempts = 3)
        `when`(operationRepository.findById(operation.id)).thenReturn(Optional.of(operation))
        `when`(courseRepository.findById(course.id)).thenReturn(Optional.of(course))

        store.markFailed(operation.id, RuntimeException("internal infrastructure detail"))

        assertEquals(CourseInfrastructureOperationStatus.FAILED, operation.status)
        assertEquals(CourseStatus.ERROR, course.status)
        verify(courseRepository).save(course)
    }

    @Test
    fun `cancelled stale completion cannot activate course`() {
        val course = course()
        val operation = operation(attempts = 1).also {
            it.status = CourseInfrastructureOperationStatus.CANCELLED
        }
        `when`(operationRepository.findById(operation.id)).thenReturn(Optional.of(operation))

        store.markSucceeded(operation.id)

        assertEquals(CourseStatus.PROVISIONING, course.status)
        verify(courseRepository, never()).save(course)
    }

    private fun operation(attempts: Int) = CourseInfrastructureOperation(
        id = 7,
        courseId = 10,
        action = CourseInfrastructureAction.PROVISION_NAMESPACE,
        status = CourseInfrastructureOperationStatus.PROCESSING,
        attempts = attempts
    )

    private fun course() = Course(
        id = 10,
        name = "Algorithms",
        code = "ALG",
        year = 2026,
        term = 1,
        professor = "Professor",
        clss = 1,
        namespaceKey = "jcode-alg-1",
        vnc = false,
        courseKey = "course-key",
        status = CourseStatus.PROVISIONING
    )
}
