package org.jbnu.jdevops.jcodeportallogin.util

import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AuthorizationUtilTest {
    private val repository = mock(UserCoursesRepository::class.java)
    private val course = Course(
        id = 10, name = "Algorithms", infrastructureKey = "ALG", year = 2026, term = 2,
        professor = "Professor", clss = 1, vnc = false, courseKey = "key"
    )

    @Test
    fun `student can view own data in enrolled course`() {
        val student = user(1, RoleType.STUDENT)
        `when`(repository.findByUserIdAndCourseId(student.id, course.id))
            .thenReturn(membership(student, RoleType.STUDENT))

        assertDoesNotThrow {
            AuthorizationUtil.validateUserAuthority(student.role, student.id, student.id, course.id, repository)
        }
    }

    @Test
    fun `assistant permission is limited to current course membership`() {
        val assistant = user(1, RoleType.STUDENT)
        `when`(repository.findByUserIdAndCourseId(assistant.id, course.id)).thenReturn(null)

        val error = assertThrows<ResponseStatusException> {
            AuthorizationUtil.validateUserAuthority(assistant.role, assistant.id, 2, course.id, repository)
        }
        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
    }

    @Test
    fun `course assistant can view another student`() {
        val assistant = user(1, RoleType.STUDENT)
        `when`(repository.findByUserIdAndCourseId(assistant.id, course.id))
            .thenReturn(membership(assistant, RoleType.ASSISTANT))

        assertDoesNotThrow {
            AuthorizationUtil.validateUserAuthority(assistant.role, assistant.id, 2, course.id, repository)
        }
    }

    private fun user(id: Long, role: RoleType) = User(
        id = id, email = "user$id@example.com", role = role, studentNum = id.toInt()
    )

    private fun membership(user: User, role: RoleType) = UserCourses(
        id = user.id, user = user, course = course, role = role
    )
}
