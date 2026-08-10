package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.dto.course.CourseDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCourseDetailsDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserInfoDto
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureAction
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.util.CourseKeyUtil
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional

@Service
class CourseService(
    private val userCoursesRepository: UserCoursesRepository,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val courseKeyUtil: CourseKeyUtil,
    private val passwordEncoder: PasswordEncoder,
    private val userRepository: UserRepository,
    private val infrastructureOperationStore: CourseInfrastructureOperationStore
) {

    private fun validateCourseManagementAuthority(courseId: Long, email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
        if (user.role == RoleType.ADMIN) {
            return
        }
        val membership = userCoursesRepository.findByUserIdAndCourseId(user.id, courseId)
        if (membership?.role != RoleType.PROFESSOR) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 담당 교수 권한이 없습니다.")
        }
    }
    // 강의별 유저 조회
    @Transactional(readOnly = true)
    fun getUsersByCourse(email: String, courseId: Long): List<UserInfoDto> {
        // 강의가 존재하는지 먼저 확인
        val userCourses = userCoursesRepository.findByCourseId(courseId)

        if (userCourses.isEmpty()) {
            return emptyList()
        }

        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")

        if (currentUser.role != RoleType.ADMIN) {
            val membership = userCoursesRepository.findByUserIdAndCourseId(currentUser.id, courseId)
            if (membership?.role !in setOf(RoleType.PROFESSOR, RoleType.ASSISTANT)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 사용자 조회 권한이 없습니다.")
            }
        }

       return userCourses.map {
           val user = it.user
           val role = it.role
           UserInfoDto(
               userId = user.id,
               name = user.name,
               email = user.email,
               role = user.role,
               courseRole = role,
               studentNum = user.studentNum
           )
       }
    }

    // 강의별 과제 조회
    @Transactional(readOnly = true)
    fun getAssignmentsByCourse(courseId: Long): List<AssignmentDto> {
        val assignments = assignmentRepository.findByCourseId(courseId)

        if (assignments.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No assignments found for this course")
        }

        return assignments.map {
            AssignmentDto(
                assignmentId = it.id,
                assignmentName = it.name,
                assignmentDescription = it.description,
                dirName = it.dirName,
                hasStarterCode = it.hasStarterCode,
                kickoffDate = it.kickoffDate,
                deadlineDate = it.deadlineDate,
                createdAt = it.createdAt.toString(),
                updatedAt = it.updatedAt.toString()
            )
        }
    }

    // 강의 key 재발급
    @Transactional
    fun reissueCourseKey(courseId: Long, email: String): String {
        validateCourseManagementAuthority(courseId, email)
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        // 새 원본 key 생성
        val newRawKey = courseKeyUtil.generateCourseEnrollmentCode(course.code, course.clss)

        // 암호화하여 업데이트
        val newEncryptedKey = passwordEncoder.encode(newRawKey)
        course.courseKey = newEncryptedKey
        courseRepository.save(course)

        return newRawKey // 새 원본 key(평문)를 반환 (관리자에게 한 번만 노출)
    }

    // 강의 추가 (DB 저장 + Generator에 NS 초기화 요청)
    @Transactional
    fun createCourse(courseDto: CourseDto, creatorEmail: String): CourseDto {
        // 랜덤 key를 생성하여 할당
        val rawKey = courseKeyUtil.generateCourseEnrollmentCode(courseDto.code, courseDto.clss)
        // PasswordEncoder를 사용해 암호화 (해싱) 처리
        val encryptedKey = passwordEncoder.encode(rawKey)

        val course = courseRepository.save(Course(
            name = courseDto.name,
            code = courseDto.code,
            professor = courseDto.professor,
            clss = courseDto.clss,
            year = courseDto.year,
            term = courseDto.term,
            vnc = courseDto.vnc,
            hwCount = courseDto.hwCount,
            pracEnabled = courseDto.pracEnabled,
            pracCount = courseDto.pracCount,
            courseKey = encryptedKey,
            status = CourseStatus.PROVISIONING
        ))

        val creator = userRepository.findByEmail(creatorEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Course creator not found")
        userCoursesRepository.save(
            UserCourses(course = course, user = creator, role = RoleType.PROFESSOR)
        )

        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.PROVISION_NAMESPACE)

        return CourseDto(
            courseId = course.id,
            name = course.name,
            code = course.code,
            professor = course.professor,
            clss = course.clss,
            year = course.year,
            term = course.term,
            vnc = course.vnc,
            hwCount = course.hwCount,
            pracEnabled = course.pracEnabled,
            pracCount = course.pracCount,
            status = course.status,
            courseKey = rawKey
        )
    }

    // 강의 수정
    @Transactional
    fun updateCourse(courseId: Long, courseDto: CourseDto, email: String): CourseDto {
        validateCourseManagementAuthority(courseId, email)
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.code != courseDto.code || course.clss != courseDto.clss || course.vnc != courseDto.vnc) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "강의 code, clss, vnc는 인프라 식별자이므로 생성 후 변경할 수 없습니다."
            )
        }

        val updatedCourse = course.copy(
            name = courseDto.name,
            year = courseDto.year,
            term = courseDto.term,
            professor = courseDto.professor,
            hwCount = courseDto.hwCount,
            pracEnabled = courseDto.pracEnabled,
            pracCount = courseDto.pracCount
        )
        courseRepository.save(updatedCourse)
        return CourseDto(
            courseId = updatedCourse.id,
            name = updatedCourse.name,
            code = updatedCourse.code,
            professor = updatedCourse.professor,
            clss = updatedCourse.clss,
            year = updatedCourse.year,
            term = updatedCourse.term,
            vnc = updatedCourse.vnc,
            hwCount = updatedCourse.hwCount,
            pracEnabled = updatedCourse.pracEnabled,
            pracCount = updatedCourse.pracCount,
            status = updatedCourse.status,
            endedAt = updatedCourse.endedAt?.toString()
        )
    }

    @Transactional
    fun deleteCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ARCHIVED) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "강의 Namespace 아카이브가 완료된 뒤에만 강의를 삭제할 수 있습니다."
            )
        }

        // 강의 삭제
        courseRepository.delete(course)
    }

    // 강의 종료 요청: DB에 desired state와 작업을 기록하고 reconciler가 완료한다.
    @Transactional
    fun endCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ACTIVE 상태의 강의만 종료할 수 있습니다.")
        }

        course.status = CourseStatus.TERMINATING
        courseRepository.save(course)
        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.DELETE_WORKLOADS)
    }

    // 강의 아카이브: status → ARCHIVED, NS 삭제
    @Transactional
    fun archiveCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ENDED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ENDED 상태의 강의만 아카이브할 수 있습니다.")
        }

        course.status = CourseStatus.ARCHIVING
        courseRepository.save(course)
        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.DELETE_NAMESPACE)
    }

    // 강의 재개설: status → ACTIVE, NS 재생성
    @Transactional
    fun reopenCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ENDED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ENDED 상태의 강의만 재개설할 수 있습니다.")
        }

        course.status = CourseStatus.PROVISIONING
        courseRepository.save(course)
        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.PROVISION_NAMESPACE)
    }

    fun retryInfrastructure(courseId: Long) {
        if (!infrastructureOperationStore.retryFailed(courseId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "재시도할 실패 작업이 없습니다.")
        }
    }

    // 전체 강의 조회
    @Transactional(readOnly = true)
    fun getAllCourses(): List<CourseDto> {
        return courseRepository.findAll()
            .map { course ->
                CourseDto(
                    courseId = course.id,
                    name = course.name,
                    code = course.code,
                    professor = course.professor,
                    term = course.term,
                    year = course.year,
                    clss = course.clss,
                    vnc = course.vnc,
                    hwCount = course.hwCount,
                    pracEnabled = course.pracEnabled,
                    pracCount = course.pracCount,
                    status = course.status,
                    endedAt = course.endedAt?.toString()
                )
            }
    }

    // 관리자용 강의 상세 정보 조회
    @Transactional(readOnly = true)
    fun getCourseDetails(courseId: Long): UserCourseDetailsDto {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val assignments = assignmentRepository.findByCourseId(courseId)
            .map { assignment ->
                AssignmentDto(
                    assignmentId = assignment.id,
                    assignmentName = assignment.name,
                    assignmentDescription = assignment.description,
                    dirName = assignment.dirName,
                    hasStarterCode = assignment.hasStarterCode,
                    kickoffDate = assignment.kickoffDate,
                    deadlineDate = assignment.deadlineDate,
                    createdAt = assignment.createdAt.toString(),
                    updatedAt = assignment.updatedAt.toString()
                )
            }

        return UserCourseDetailsDto(
            courseId = course.id,
            courseName = course.name,
            courseCode = course.code,
            courseProfessor = course.professor,
            courseYear = course.year,
            courseTerm = course.term,
            courseClss = course.clss,
            hwCount = course.hwCount,
            pracEnabled = course.pracEnabled,
            pracCount = course.pracCount,
            status = course.status,
            assignments = assignments,
            jcodeUrl = null // 관리자는 JCode URL이 필요 없음
        )
    }
}
