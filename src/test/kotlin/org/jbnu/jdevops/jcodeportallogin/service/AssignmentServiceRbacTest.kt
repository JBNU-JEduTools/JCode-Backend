package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional

class AssignmentServiceRbacTest {
    private val assignmentRepository = mock(AssignmentRepository::class.java)
    private val courseRepository = mock(CourseRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val userCoursesRepository = mock(UserCoursesRepository::class.java)
    private val generatorWebClient = WebClient.builder().build()

    private val service = AssignmentService(
        assignmentRepository,
        courseRepository,
        userRepository,
        userCoursesRepository,
        generatorWebClient
    )

    @Test
    fun `assistant in another course cannot delete assignment`() {
        val assistant = user(1, "ta@example.com", RoleType.STUDENT)
        val targetCourseId = 20L

        `when`(userRepository.findByEmail(assistant.email)).thenReturn(assistant)
        `when`(userCoursesRepository.findByUserIdAndCourseId(assistant.id, targetCourseId)).thenReturn(null)

        val ex = assertThrows<ResponseStatusException> {
            service.deleteAssignment(targetCourseId, 100L, assistant.email)
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(assignmentRepository, never()).delete(org.mockito.ArgumentMatchers.any(Assignment::class.java))
    }

    @Test
    fun `global professor without course professor membership cannot manage assignment`() {
        val professor = user(1, "professor@example.com", RoleType.PROFESSOR)
        `when`(userRepository.findByEmail(professor.email)).thenReturn(professor)
        `when`(userCoursesRepository.findByUserIdAndCourseId(professor.id, 20L)).thenReturn(null)

        val ex = assertThrows<ResponseStatusException> {
            service.deleteAssignment(20L, 100L, professor.email)
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(assignmentRepository, never()).delete(org.mockito.ArgumentMatchers.any(Assignment::class.java))
    }

    @Test
    fun `course assistant can delete assignment in managed course`() {
        val assistant = user(1, "ta@example.com", RoleType.STUDENT)
        val courseId = 10L
        val assignmentId = 100L

        `when`(userRepository.findByEmail(assistant.email)).thenReturn(assistant)
        val course = course(courseId)
        val assignment = assignment(assignmentId, course, "title", "dir")
        `when`(userCoursesRepository.findByUserIdAndCourseId(assistant.id, courseId))
            .thenReturn(userCourse(assistant, course, RoleType.ASSISTANT))
        `when`(courseRepository.findById(courseId)).thenReturn(Optional.of(course))
        `when`(assignmentRepository.findByIdAndCourseId(assignmentId, courseId)).thenReturn(Optional.of(assignment))

        service.deleteAssignment(courseId, assignmentId, assistant.email)

        verify(assignmentRepository).delete(assignment)
    }

    @Test
    fun `course assistant can update assignment display title without changing dir name`() {
        val assistant = user(1, "ta@example.com", RoleType.STUDENT)
        val course = course(10)
        val assignment = assignment(100, course, "old title", "old-dir")
        val dto = org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto(
            assignmentName = "new title",
            assignmentDescription = "updated",
            kickoffDate = assignment.kickoffDate,
            deadlineDate = assignment.deadlineDate
        )

        `when`(userRepository.findByEmail(assistant.email)).thenReturn(assistant)
        `when`(userCoursesRepository.findByUserIdAndCourseId(assistant.id, course.id))
            .thenReturn(userCourse(assistant, course, RoleType.ASSISTANT))
        `when`(courseRepository.findById(course.id)).thenReturn(Optional.of(course))
        `when`(assignmentRepository.findByIdAndCourseId(assignment.id, course.id)).thenReturn(Optional.of(assignment))
        `when`(assignmentRepository.save(org.mockito.ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { it.arguments[0] as Assignment }

        val result = service.updateAssignment(course.id, assignment.id, dto, assistant.email)

        assertEquals("new title", result.assignmentName)
        assertEquals("old-dir", result.dirName)
        assert(result.updatedAt != assignment.updatedAt.toString())
    }

    private fun course(id: Long) = Course(
        id = id,
        name = "Algorithms",
        code = "ALG",
        year = 2026,
        term = 1,
        professor = "Professor",
        clss = 1,
        vnc = false,
        courseKey = "course-key"
    )

    private fun user(id: Long, email: String, role: RoleType) = User(
        id = id,
        email = email,
        role = role,
        studentNum = id.toInt()
    )

    private fun assignment(id: Long, course: Course, name: String, dirName: String) = Assignment(
        id = id,
        course = course,
        name = name,
        description = "description",
        dirName = dirName,
        kickoffDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        deadlineDate = LocalDateTime.of(2026, 1, 2, 0, 0)
    )

    private fun userCourse(user: User, course: Course, role: RoleType) = UserCourses(
        id = user.id,
        user = user,
        course = course,
        role = role
    )
}
