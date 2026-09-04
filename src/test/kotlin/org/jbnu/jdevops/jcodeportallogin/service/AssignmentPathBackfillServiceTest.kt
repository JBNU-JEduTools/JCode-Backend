package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentPathBackfillStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentScheduleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationAction
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class AssignmentPathBackfillServiceTest {
    private val assignmentRepository = mock(AssignmentRepository::class.java)
    private val operationStore = mock(WorkspaceOperationStore::class.java)
    private val namespaceLookup = mock(CourseNamespaceLookup::class.java)
    private val service = AssignmentPathBackfillService(assignmentRepository, operationStore, namespaceLookup)

    @Test
    fun `active course with namespace registers migration once per assignment`() {
        val course = course(status = CourseStatus.ACTIVE)
        val open = assignment(11, course, AssignmentScheduleStatus.OPEN)
        val closed = assignment(12, course, AssignmentScheduleStatus.CLOSED)
        `when`(assignmentRepository.findByPathBackfillStatus(AssignmentPathBackfillStatus.PENDING))
            .thenReturn(listOf(open, closed))
        `when`(namespaceLookup.exists(course)).thenReturn(true)

        val result = service.execute(MissingNamespacePolicy.FAIL)

        assertEquals(2, result.registered)
        assertEquals(0, result.archived)
        assertEquals(AssignmentPathBackfillStatus.REGISTERED, open.pathBackfillStatus)
        assertEquals(AssignmentPathBackfillStatus.REGISTERED, closed.pathBackfillStatus)
        verify(namespaceLookup, times(1)).exists(course)
        verify(operationStore).enqueueBackfillOnce(11, WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH)
        verify(operationStore).enqueueBackfillOnce(12, WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH)
        verify(operationStore).enqueueBackfillOnce(12, WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION)
    }

    @Test
    fun `missing active namespace fails before database changes by default`() {
        val course = course(status = CourseStatus.ACTIVE)
        val assignment = assignment(11, course, AssignmentScheduleStatus.OPEN)
        `when`(assignmentRepository.findByPathBackfillStatus(AssignmentPathBackfillStatus.PENDING))
            .thenReturn(listOf(assignment))
        `when`(namespaceLookup.exists(course)).thenReturn(false)

        assertThrows<IllegalStateException> {
            service.execute(MissingNamespacePolicy.FAIL)
        }

        assertEquals(AssignmentPathBackfillStatus.PENDING, assignment.pathBackfillStatus)
        verify(assignmentRepository, never()).save(assignment)
        verifyNoInteractions(operationStore)
    }

    @Test
    fun `explicit archive policy archives assignments without namespaces`() {
        val course = course(status = CourseStatus.ACTIVE)
        val assignment = assignment(11, course, AssignmentScheduleStatus.CLOSED)
        `when`(assignmentRepository.findByPathBackfillStatus(AssignmentPathBackfillStatus.PENDING))
            .thenReturn(listOf(assignment))
        `when`(namespaceLookup.exists(course)).thenReturn(false)

        val result = service.execute(MissingNamespacePolicy.ARCHIVE)

        assertEquals(0, result.registered)
        assertEquals(1, result.archived)
        assertEquals(AssignmentLifecycleStatus.ARCHIVED, assignment.lifecycleStatus)
        assertEquals(AssignmentScheduleStatus.ARCHIVED, assignment.scheduleStatus)
        assertEquals(AssignmentPathBackfillStatus.ARCHIVED, assignment.pathBackfillStatus)
        assertNotNull(assignment.archivedAt)
        verifyNoInteractions(operationStore)
    }

    @Test
    fun `ended courses are archived without namespace lookup`() {
        val course = course(status = CourseStatus.ENDED)
        val assignment = assignment(11, course, AssignmentScheduleStatus.CLOSED)
        `when`(assignmentRepository.findByPathBackfillStatus(AssignmentPathBackfillStatus.PENDING))
            .thenReturn(listOf(assignment))

        service.execute(MissingNamespacePolicy.FAIL)

        assertEquals(AssignmentLifecycleStatus.ARCHIVED, assignment.lifecycleStatus)
        assertEquals(AssignmentPathBackfillStatus.ARCHIVED, assignment.pathBackfillStatus)
        verify(namespaceLookup, never()).exists(course)
    }

    private fun course(status: CourseStatus) = Course(
        id = 7,
        name = "Algorithms",
        code = "ALG",
        year = 2026,
        term = 1,
        professor = "Professor",
        clss = 1,
        vnc = false,
        status = status,
        courseKey = "key"
    )

    private fun assignment(id: Long, course: Course, scheduleStatus: AssignmentScheduleStatus) = Assignment(
        id = id,
        course = course,
        name = "Homework $id",
        description = null,
        workspaceKey = "assignment-$id",
        legacyDirName = "Homework $id",
        pathBackfillStatus = AssignmentPathBackfillStatus.PENDING,
        lifecycleStatus = AssignmentLifecycleStatus.PROVISIONING,
        scheduleStatus = scheduleStatus,
        kickoffDate = LocalDateTime.now().minusDays(2),
        deadlineDate = LocalDateTime.now().minusDays(1)
    )
}
