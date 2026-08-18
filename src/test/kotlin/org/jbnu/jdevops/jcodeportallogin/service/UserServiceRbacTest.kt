package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.entity.MembershipStatus
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationAction
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationTarget
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.JCodeRepository
import org.jbnu.jdevops.jcodeportallogin.repo.LoginRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

@Suppress("UNCHECKED_CAST")
class UserServiceRbacTest {
    private val userRepository = mock(UserRepository::class.java)
    private val loginRepository = mock(LoginRepository::class.java)
    private val jcodeRepository = mock(JCodeRepository::class.java)
    private val userCoursesRepository = mock(UserCoursesRepository::class.java)
    private val assignmentRepository = mock(AssignmentRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val courseRepository = mock(CourseRepository::class.java)
    private val redisTemplate = mock(StringRedisTemplate::class.java)
    private val setOperations = mock(SetOperations::class.java) as SetOperations<String, String>
    private val redisService = RedisService(redisTemplate)
    private val jCodeService = mock(JCodeService::class.java)
    private val workspaceOperationStore = mock(WorkspaceOperationStore::class.java)

    private val service = UserService(
        userRepository,
        loginRepository,
        jcodeRepository,
        userCoursesRepository,
        assignmentRepository,
        passwordEncoder,
        courseRepository,
        redisService,
        jCodeService,
        workspaceOperationStore
    )

    init {
        `when`(redisTemplate.opsForSet()).thenReturn(setOperations)
    }

    @Test
    fun `professor cannot chase out student from unmanaged course`() {
        val course = course()
        val professor = user(1, "professor@example.com", RoleType.PROFESSOR)
        val student = user(2, "student@example.com", RoleType.STUDENT)
        val targetUserCourse = userCourse(student, course, RoleType.STUDENT)

        `when`(userRepository.findByEmail(professor.email)).thenReturn(professor)
        `when`(userRepository.findById(student.id)).thenReturn(student)
        `when`(courseRepository.findById(course.id)).thenReturn(Optional.of(course))
        `when`(userCoursesRepository.findByUserIdAndCourseId(professor.id, course.id)).thenReturn(null)
        `when`(userCoursesRepository.findByUserIdAndCourseId(student.id, course.id)).thenReturn(targetUserCourse)

        val ex = assertThrows<ResponseStatusException> {
            service.chaseOutCourse(student.id, course.id, professor.email, "token")
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(userCoursesRepository, never()).delete(any(UserCourses::class.java))
    }

    @Test
    fun `course professor can chase out enrolled student from managed course`() {
        val course = course()
        val professor = user(1, "professor@example.com", RoleType.PROFESSOR)
        val student = user(2, "student@example.com", RoleType.STUDENT)
        val professorCourse = userCourse(professor, course, RoleType.PROFESSOR)
        val targetUserCourse = userCourse(student, course, RoleType.STUDENT)

        `when`(userRepository.findByEmail(professor.email)).thenReturn(professor)
        `when`(userRepository.findById(student.id)).thenReturn(student)
        `when`(courseRepository.findById(course.id)).thenReturn(Optional.of(course))
        `when`(userCoursesRepository.findByUserIdAndCourseId(professor.id, course.id)).thenReturn(professorCourse)
        `when`(userCoursesRepository.findByUserIdAndCourseId(student.id, course.id)).thenReturn(targetUserCourse)
        val result = service.chaseOutCourse(student.id, course.id, professor.email, "token")

        assertEquals(course.id, result)
        assertEquals(MembershipStatus.DELETE_PENDING, targetUserCourse.lifecycleStatus)
        verify(userCoursesRepository).save(targetUserCourse)
        verify(workspaceOperationStore).enqueue(
            WorkspaceOperationTarget.MEMBERSHIP,
            targetUserCourse.id,
            WorkspaceOperationAction.DELETE_MEMBERSHIP
        )
        verify(redisTemplate).delete("user:${student.email}:course:${course.code}:${course.clss}")
        verify(redisTemplate).delete("user:${student.email}:course:${course.code}:${course.clss}:snapshot")
        verify(setOperations).remove("course:${course.code}:${course.clss}:managers", student.email)
    }

    @Test
    fun `user cannot join ended course`() {
        val course = course().apply { status = CourseStatus.ENDED }
        val user = user(2, "student@example.com", RoleType.STUDENT)
        val rawKey = "ALG-1-secret"

        `when`(courseRepository.findByCodeAndClss(course.code, course.clss)).thenReturn(listOf(course))
        `when`(passwordEncoder.matches(rawKey, course.courseKey)).thenReturn(true)
        `when`(userRepository.findByEmail(user.email)).thenReturn(user)

        val ex = assertThrows<ResponseStatusException> {
            service.joinCourse(user.email, rawKey)
        }

        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        verify(userCoursesRepository, never()).save(any(UserCourses::class.java))
    }

    private fun course() = Course(
        id = 10,
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

    private fun userCourse(user: User, course: Course, role: RoleType) = UserCourses(
        id = user.id,
        user = user,
        course = course,
        role = role
    )
}
