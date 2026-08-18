package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.course.CourseDto
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureAction
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentScheduleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.Jcode
import org.jbnu.jdevops.jcodeportallogin.entity.JcodeLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.entity.CourseEnvironmentProfile
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.util.CourseKeyUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.ArgumentCaptor
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.time.LocalDateTime

class CourseServiceHardeningTest {
    private val userCoursesRepository = mock(UserCoursesRepository::class.java)
    private val assignmentRepository = mock(AssignmentRepository::class.java)
    private val courseRepository = mock(CourseRepository::class.java)
    private val courseKeyUtil = mock(CourseKeyUtil::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val infrastructureOperationStore = mock(CourseInfrastructureOperationStore::class.java)
    private val starterArtifactRepository = mock(org.jbnu.jdevops.jcodeportallogin.repo.StarterArtifactRepository::class.java)
    private val jCodeRepository = mock(org.jbnu.jdevops.jcodeportallogin.repo.JCodeRepository::class.java)
    private val workspaceOperationStore = mock(WorkspaceOperationStore::class.java)
    private val service = CourseService(
        userCoursesRepository,
        assignmentRepository,
        courseRepository,
        courseKeyUtil,
        passwordEncoder,
        userRepository,
        starterArtifactRepository,
        jCodeRepository,
        workspaceOperationStore,
        infrastructureOperationStore,
    )

    @Test
    fun `course infrastructure identifiers cannot be edited`() {
        val course = course()
        val admin = User(id = 1, email = "admin@example.com", role = RoleType.ADMIN, studentNum = 1)
        val request = dto(code = "NEWCODE", clss = course.clss, vnc = course.vnc)
        `when`(userRepository.findByEmail(admin.email)).thenReturn(admin)
        `when`(courseRepository.findById(course.id)).thenReturn(Optional.of(course))

        val ex = assertThrows<ResponseStatusException> {
            service.updateCourse(course.id, request, admin.email)
        }

        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any(Course::class.java))
    }

    @Test
    fun `course creator is registered as course professor`() {
        val creator = User(id = 7, email = "professor@example.com", role = RoleType.PROFESSOR)
        val savedCourse = course().copy(id = 44, status = CourseStatus.PROVISIONING)
        `when`(courseKeyUtil.generateCourseEnrollmentCode("ALG", 1)).thenReturn("raw-key")
        `when`(passwordEncoder.encode("raw-key")).thenReturn("encoded-key")
        `when`(courseRepository.save(org.mockito.ArgumentMatchers.any(Course::class.java))).thenReturn(savedCourse)
        `when`(userRepository.findByEmail(creator.email)).thenReturn(creator)

        val result = service.createCourse(dto(code = "ALG", clss = 1, vnc = false), creator.email)

        val membership = ArgumentCaptor.forClass(UserCourses::class.java)
        verify(userCoursesRepository).save(membership.capture())
        assertEquals(44, result.courseId)
        assertEquals(savedCourse, membership.value.course)
        assertEquals(creator, membership.value.user)
        assertEquals(RoleType.PROFESSOR, membership.value.role)
        assertEquals(CourseStatus.PROVISIONING, result.status)
        verify(infrastructureOperationStore).enqueue(44, CourseInfrastructureAction.PROVISION_NAMESPACE)
    }

    @Test
    fun `legacy vnc course is converted to lab profile`() {
        val creator = User(id = 7, email = "professor@example.com", role = RoleType.PROFESSOR)
        `when`(courseKeyUtil.generateCourseEnrollmentCode("LAB", 1)).thenReturn("raw-key")
        `when`(passwordEncoder.encode("raw-key")).thenReturn("encoded-key")
        `when`(userRepository.findByEmail(creator.email)).thenReturn(creator)
        `when`(courseRepository.save(org.mockito.ArgumentMatchers.any(Course::class.java))).thenAnswer {
            (it.arguments[0] as Course).copy(id = 45)
        }

        val result = service.createCourse(dto(code = "LAB", clss = 1, vnc = true), creator.email)

        assertEquals(CourseEnvironmentProfile.LAB, result.environmentProfile)
        assertEquals(true, result.useVnc)
        assertEquals(true, result.useJupyter)
        assertEquals(org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceResourceProfile.STANDARD, result.resourceProfile)
    }

    @Test
    fun `custom profile rejects mutable image tag`() {
        val request = dto(code = "CUSTOM", clss = 1, vnc = false).copy(
            environmentProfile = CourseEnvironmentProfile.CUSTOM,
            useVnc = false,
            useJupyter = true,
            baseImage = "harbor.jedutools.io/jdevops/custom:latest",
            resourceProfile = org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceResourceProfile.GPU,
            egressPolicy = org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceEgressPolicy.RESTRICTED,
            workspaceScope = org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceScope.ASSIGNMENT
        )

        val error = assertThrows<ResponseStatusException> {
            service.createCourse(request, "professor@example.com")
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `custom profile accepts immutable image from configured registry`() {
        val creator = User(id = 7, email = "professor@example.com", role = RoleType.PROFESSOR)
        val configuredService = CourseService(
            userCoursesRepository,
            assignmentRepository,
            courseRepository,
            courseKeyUtil,
            passwordEncoder,
            userRepository,
            starterArtifactRepository,
            jCodeRepository,
            workspaceOperationStore,
            infrastructureOperationStore,
            "registry.internal:5443",
        )
        val request = dto(code = "CUSTOM", clss = 1, vnc = false).copy(
            environmentProfile = CourseEnvironmentProfile.CUSTOM,
            useVnc = false,
            useJupyter = true,
            baseImage = "registry.internal:5443/jdevops/custom:build-0123456789abcdef",
            resourceProfile = org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceResourceProfile.GPU,
            egressPolicy = org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceEgressPolicy.RESTRICTED,
            workspaceScope = org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceScope.ASSIGNMENT
        )
        `when`(courseKeyUtil.generateCourseEnrollmentCode("CUSTOM", 1)).thenReturn("raw-key")
        `when`(passwordEncoder.encode("raw-key")).thenReturn("encoded-key")
        `when`(userRepository.findByEmail(creator.email)).thenReturn(creator)
        `when`(courseRepository.save(org.mockito.ArgumentMatchers.any(Course::class.java))).thenAnswer {
            (it.arguments[0] as Course).copy(id = 46)
        }

        val result = configuredService.createCourse(request, creator.email)

        assertEquals("registry.internal:5443/jdevops/custom:build-0123456789abcdef", result.baseImage)
    }

    @Test
    fun `ending a course records desired state before infrastructure work`() {
        val course = course()
        `when`(courseRepository.findById(course.id)).thenReturn(Optional.of(course))
        `when`(assignmentRepository.findByCourseId(course.id)).thenReturn(emptyList())
        `when`(jCodeRepository.findByCourseId(course.id)).thenReturn(emptyList())

        service.endCourse(course.id)

        assertEquals(CourseStatus.TERMINATING, course.status)
        verify(courseRepository).save(course)
        verify(infrastructureOperationStore).enqueue(course.id, CourseInfrastructureAction.DELETE_WORKLOADS)
    }

    @Test
    fun `ending a course preserves records and queues workspace cleanup`() {
        val course = course()
        val user = User(id = 2, email = "student@example.com", role = RoleType.STUDENT, studentNum = 20260001)
        val membership = UserCourses(id = 3, course = course, user = user, role = RoleType.STUDENT)
        val assignment = Assignment(
            id = 4,
            course = course,
            name = "과제",
            description = null,
            workspaceKey = "assignment-4",
            dirName = "assignment-4",
            lifecycleStatus = AssignmentLifecycleStatus.ACTIVE,
            scheduleStatus = AssignmentScheduleStatus.OPEN,
            kickoffDate = LocalDateTime.now().minusDays(1),
            deadlineDate = LocalDateTime.now().plusDays(1)
        )
        val jcode = Jcode(
            id = 5,
            userCourse = membership,
            course = course,
            user = user,
            jcodeUrl = "http://jcode",
            lifecycleStatus = JcodeLifecycleStatus.READY
        )
        `when`(courseRepository.findById(course.id)).thenReturn(Optional.of(course))
        `when`(assignmentRepository.findByCourseId(course.id)).thenReturn(listOf(assignment))
        `when`(jCodeRepository.findByCourseId(course.id)).thenReturn(listOf(jcode))

        service.endCourse(course.id)

        assertEquals(AssignmentScheduleStatus.CLOSED, assignment.scheduleStatus)
        assertEquals(JcodeLifecycleStatus.DELETE_PENDING, jcode.lifecycleStatus)
        verify(workspaceOperationStore).enqueue(
            org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationTarget.ASSIGNMENT,
            assignment.id,
            org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION
        )
        verify(workspaceOperationStore).enqueue(
            org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationTarget.JCODE,
            jcode.id,
            org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationAction.DELETE_JCODE
        )
        verify(jCodeRepository, never()).delete(org.mockito.ArgumentMatchers.any(Jcode::class.java))
    }

    @Test
    fun `failed infrastructure work can be retried`() {
        `when`(infrastructureOperationStore.retryFailed(10)).thenReturn(true)

        service.retryInfrastructure(10)

        verify(infrastructureOperationStore).retryFailed(10)
    }

    @Test
    fun `retry is rejected when no failed infrastructure work exists`() {
        `when`(infrastructureOperationStore.retryFailed(10)).thenReturn(false)

        val ex = assertThrows<ResponseStatusException> { service.retryInfrastructure(10) }

        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
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
        courseKey = "course-key",
    )

    private fun dto(code: String, clss: Int, vnc: Boolean) = CourseDto(
        courseId = 10,
        name = "Algorithms",
        code = code,
        professor = "Professor",
        year = 2026,
        term = 1,
        clss = clss,
        vnc = vnc,
    )
}
