package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.dto.course.CourseDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCourseDetailsDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserInfoDto
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.JCodeRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.util.CourseKeyUtil
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CourseService(
    private val userCoursesRepository: UserCoursesRepository,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val jCodeRepository: JCodeRepository,
    private val courseKeyUtil: CourseKeyUtil,
    private val passwordEncoder: PasswordEncoder,
    private val userRepository: UserRepository,
    @Qualifier("generatorWebClient")
    private val generatorWebClient: WebClient
) {

    private fun getCourseNamespace(course: Course): String {
        return "jcode-${course.code.lowercase()}-${course.clss}"
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

        // STUDENT인 경우 해당 강의에서 조교 역할인지 확인
        if (currentUser.role == RoleType.STUDENT) {
            val isAssistantInCourse = userCoursesRepository.existsByCourseIdAndUserIdAndRole(courseId, currentUser.id, RoleType.ASSISTANT)
            if (!isAssistantInCourse) {
                return emptyList()
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
    fun reissueCourseKey(courseId: Long): String {
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
    fun createCourse(courseDto: CourseDto, token: String? = null): CourseDto {
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
            courseKey = encryptedKey
        ))

        // Generator에 NS 초기화 요청 (실패해도 강의 생성은 유지 — JCode 배포 시 자동 생성 fallback 있음)
        if (token != null) {
            try {
                val namespace = getCourseNamespace(course)
                generatorWebClient.post()
                    .uri("/api/namespace")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(mapOf("namespace" to namespace))
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block()
            } catch (ex: Exception) {
                // NS 생성 실패는 치명적이지 않음 — JCode 배포 시 자동 생성됨
                println("Warning: NS 초기화 실패 (JCode 배포 시 자동 생성됩니다): ${ex.message}")
            }
        }

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
    fun updateCourse(courseId: Long, courseDto: CourseDto): CourseDto {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }
        val updatedCourse = course.copy(
            name = courseDto.name,
            code = courseDto.code,
            clss = courseDto.clss,
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
    fun deleteCourse(courseId: Long, token: String? = null) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        // ARCHIVED 상태가 아니면 NS도 삭제
        if (course.status != CourseStatus.ARCHIVED && token != null) {
            try {
                val namespace = getCourseNamespace(course)
                generatorWebClient.delete()
                    .uri("/api/namespace/$namespace")
                    .header("Authorization", "Bearer $token")
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block()
            } catch (ex: Exception) {
                println("Warning: NS 삭제 실패: ${ex.message}")
            }
        }

        // 강의 삭제
        courseRepository.delete(course)
    }

    // 강의 종료: status → ENDED, pod 전체 삭제, jcode 레코드 삭제
    @Transactional
    fun endCourse(courseId: Long, token: String) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ACTIVE 상태의 강의만 종료할 수 있습니다.")
        }

        // Generator에 NS 내 전체 리소스 삭제 요청 (NS는 유지)
        try {
            val namespace = getCourseNamespace(course)
            generatorWebClient.delete()
                .uri("/api/namespace/$namespace/resources")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
        } catch (ex: Exception) {
            println("Warning: NS 리소스 삭제 실패: ${ex.message}")
        }

        // jcode 테이블에서 해당 course의 모든 레코드 삭제
        val jcodes = jCodeRepository.findByCourseId(courseId)
        jCodeRepository.deleteAll(jcodes)

        // 상태 변경
        course.status = CourseStatus.ENDED
        course.endedAt = LocalDateTime.now()
        courseRepository.save(course)
    }

    // 강의 아카이브: status → ARCHIVED, NS 삭제
    @Transactional
    fun archiveCourse(courseId: Long, token: String) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ENDED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ENDED 상태의 강의만 아카이브할 수 있습니다.")
        }

        // Generator에 NS 삭제 요청
        try {
            val namespace = getCourseNamespace(course)
            generatorWebClient.delete()
                .uri("/api/namespace/$namespace")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
        } catch (ex: Exception) {
            println("Warning: NS 삭제 실패: ${ex.message}")
        }

        course.status = CourseStatus.ARCHIVED
        courseRepository.save(course)
    }

    // 강의 재개설: status → ACTIVE, NS 재생성
    @Transactional
    fun reopenCourse(courseId: Long, token: String) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ENDED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ENDED 상태의 강의만 재개설할 수 있습니다.")
        }

        // Generator에 NS 재생성 요청
        try {
            val namespace = getCourseNamespace(course)
            generatorWebClient.post()
                .uri("/api/namespace")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("namespace" to namespace))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NS 재생성 실패: ${ex.message}")
        }

        course.status = CourseStatus.ACTIVE
        course.endedAt = null
        courseRepository.save(course)
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
