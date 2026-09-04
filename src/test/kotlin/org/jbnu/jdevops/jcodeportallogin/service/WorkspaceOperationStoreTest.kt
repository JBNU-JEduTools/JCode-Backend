package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.*
import org.jbnu.jdevops.jcodeportallogin.repo.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class WorkspaceOperationStoreTest {
    private val operationRepository = mock(WorkspaceOperationRepository::class.java)
    private val assignmentRepository = mock(AssignmentRepository::class.java)
    private val artifactRepository = mock(StarterArtifactRepository::class.java)
    private val membershipRepository = mock(UserCoursesRepository::class.java)
    private val jcodeRepository = mock(JCodeRepository::class.java)
    private val store = WorkspaceOperationStore(
        operationRepository,
        assignmentRepository,
        artifactRepository,
        membershipRepository,
        jcodeRepository,
        3,
        300
    )

    @Test
    fun `completed stale provision does not undo assignment deletion`() {
        val assignment = assignment().also { it.lifecycleStatus = AssignmentLifecycleStatus.DELETING }
        val stored = operation(10, WorkspaceOperationTarget.ASSIGNMENT, assignment.id, WorkspaceOperationAction.PROVISION_ASSIGNMENT)
        val claimed = claimed(stored)
        `when`(operationRepository.findById(stored.id)).thenReturn(Optional.of(stored))
        `when`(assignmentRepository.findById(assignment.id)).thenReturn(Optional.of(assignment))

        store.markSucceeded(claimed, mapOf("ok" to true))

        assertEquals(AssignmentLifecycleStatus.DELETING, assignment.lifecycleStatus)
        assertEquals(WorkspaceOperationStatus.SUCCEEDED, stored.status)
    }

    @Test
    fun `in flight jcode provision is compensated after membership deletion starts`() {
        val course = course()
        val user = User(id = 2, email = "student@example.com", role = RoleType.STUDENT, studentNum = 20260001)
        val membership = UserCourses(
            id = 3,
            course = course,
            user = user,
            role = RoleType.STUDENT,
            lifecycleStatus = MembershipStatus.DELETE_PENDING
        )
        val jcode = Jcode(
            id = 4,
            userCourse = membership,
            course = course,
            user = user,
            lifecycleStatus = JcodeLifecycleStatus.PROVISIONING
        )
        val stored = operation(11, WorkspaceOperationTarget.JCODE, jcode.id, WorkspaceOperationAction.PROVISION_JCODE)
        `when`(operationRepository.findById(stored.id)).thenReturn(Optional.of(stored))
        `when`(jcodeRepository.findById(jcode.id)).thenReturn(Optional.of(jcode))

        store.markSucceeded(claimed(stored), mapOf("jcodeUrl" to "http://example"))

        assertEquals(JcodeLifecycleStatus.DELETE_PENDING, jcode.lifecycleStatus)
        val operations = ArgumentCaptor.forClass(WorkspaceOperation::class.java)
        verify(operationRepository, times(2)).save(operations.capture())
        assertEquals(WorkspaceOperationAction.DELETE_JCODE, operations.allValues.first().action)
        assertEquals(WorkspaceOperationStatus.SUCCEEDED, operations.allValues.last().status)
    }

    @Test
    fun `backfill operation is not duplicated on rerun`() {
        store.enqueueBackfillOnce(5, WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH)

        val operation = ArgumentCaptor.forClass(WorkspaceOperation::class.java)
        verify(operationRepository).save(operation.capture())
        assertEquals(36, operation.value.idempotencyKey.length)
        assertEquals(WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH, operation.value.action)

        `when`(operationRepository.existsByIdempotencyKey(operation.value.idempotencyKey)).thenReturn(true)
        store.enqueueBackfillOnce(5, WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH)

        verify(operationRepository, times(1)).save(org.mockito.ArgumentMatchers.any(WorkspaceOperation::class.java))
    }

    @Test
    fun `readiness polling is deferred without consuming retry attempts`() {
        val stored = operation(
            12,
            WorkspaceOperationTarget.JCODE,
            4,
            WorkspaceOperationAction.PROVISION_JCODE
        ).also {
            it.status = WorkspaceOperationStatus.PROCESSING
            it.attempts = 1
        }
        `when`(operationRepository.findById(stored.id)).thenReturn(Optional.of(stored))

        store.defer(claimed(stored))

        assertEquals(WorkspaceOperationStatus.PENDING, stored.status)
        assertEquals(0, stored.attempts)
        assertEquals(null, stored.lockedAt)
    }

    private fun claimed(operation: WorkspaceOperation) = ClaimedWorkspaceOperation(
        operation.id,
        operation.targetType,
        operation.targetId,
        operation.action,
        operation.artifactId,
        operation.idempotencyKey,
        1
    )

    private fun operation(id: Long, target: WorkspaceOperationTarget, targetId: Long, action: WorkspaceOperationAction) =
        WorkspaceOperation(id = id, targetType = target, targetId = targetId, action = action)

    private fun course() = Course(
        id = 1,
        name = "Algorithms",
        code = "ALG",
        year = 2026,
        term = 1,
        professor = "Professor",
        clss = 1,
        vnc = false,
        courseKey = "key"
    )

    private fun assignment() = Assignment(
        id = 5,
        course = course(),
        name = "Homework",
        description = null,
        kickoffDate = java.time.LocalDateTime.now().minusDays(1),
        deadlineDate = java.time.LocalDateTime.now().plusDays(1)
    )
}
