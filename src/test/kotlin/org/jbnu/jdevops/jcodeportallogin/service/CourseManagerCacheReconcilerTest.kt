package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.MembershipStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class CourseManagerCacheReconcilerTest {
    private val courseRepository = mock(CourseRepository::class.java)
    private val userCoursesRepository = mock(UserCoursesRepository::class.java)
    private val redisService = mock(RedisService::class.java)
    private val reconciler = CourseManagerCacheReconciler(
        courseRepository,
        userCoursesRepository,
        redisService
    )

    @Test
    fun `rebuilds each course manager set from active ready memberships`() {
        val activeCourse = course(10, "OS", 4, CourseStatus.ACTIVE)
        val previousCourseWithSameKey = course(11, "OS", 4, CourseStatus.ARCHIVED)
        val endedCourse = course(12, "OLD", 1, CourseStatus.ENDED)
        val professor = membership(1, "professor@example.com", activeCourse, RoleType.PROFESSOR)
        val assistant = membership(2, "assistant@example.com", activeCourse, RoleType.ASSISTANT)

        `when`(courseRepository.findAll()).thenReturn(
            listOf(activeCourse, previousCourseWithSameKey, endedCourse)
        )
        `when`(
            userCoursesRepository.findActiveCourseManagers(
                setOf(RoleType.PROFESSOR, RoleType.ASSISTANT),
                MembershipStatus.READY,
                CourseStatus.ACTIVE
            )
        ).thenReturn(listOf(professor, assistant))

        reconciler.reconcile()

        verify(redisService, times(1)).replaceCourseManagers(
            "OS",
            4,
            setOf("professor@example.com", "assistant@example.com")
        )
        verify(redisService).replaceCourseManagers("OLD", 1, emptySet())
    }

    private fun course(id: Long, code: String, clss: Int, status: CourseStatus) = Course(
        id = id,
        name = code,
        infrastructureKey = code,
        year = 2026,
        term = 1,
        professor = "Professor",
        clss = clss,
        vnc = false,
        status = status,
        courseKey = "course-key"
    )

    private fun membership(id: Long, email: String, course: Course, role: RoleType) = UserCourses(
        id = id,
        user = User(id = id, email = email, role = RoleType.STUDENT, studentNum = id.toInt()),
        course = course,
        role = role,
        lifecycleStatus = MembershipStatus.READY
    )
}
