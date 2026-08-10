package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.dto.course.CourseDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserInfoDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCourseDetailsDto
import org.jbnu.jdevops.jcodeportallogin.service.CourseService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@Tag(name = "Course API", description = "강의 관련 API")
@RestController
@RequestMapping("/api/courses")
class CourseController(
    private val courseService: CourseService
) {
    // 전체 강의 목록 조회 (ADMIN 전용)
    @Operation(
        summary = "전체 강의 목록 조회",
        description = "시스템에 등록된 모든 강의 목록을 조회합니다. (ADMIN 전용)"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    fun getAllCourses(): ResponseEntity<List<CourseDto>> {
        return ResponseEntity.ok(courseService.getAllCourses())
    }

    // 강의별 유저 조회 (학생 조회 불가)
    @Operation(
        summary = "강의별 유저 조회",
        description = "특정 강의에 등록된 모든 사용자 정보를 조회합니다. (STUDENT 제외)"
    )
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{courseId}/users")
    fun getUsersByCourse(@PathVariable courseId: Long, authentication: Authentication): ResponseEntity<List<UserInfoDto>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        return ResponseEntity.ok(courseService.getUsersByCourse(email, courseId))
    }

    // 강의별 과제 조회
    @Operation(
        summary = "강의별 과제 조회",
        description = "특정 강의에 등록된 모든 과제 정보를 조회합니다."
    )
    @GetMapping("/{courseId}/assignments")
    fun getAssignmentsByCourse(@PathVariable courseId: Long): ResponseEntity<List<AssignmentDto>> {
        return ResponseEntity.ok(courseService.getAssignmentsByCourse(courseId))
    }

    // 강의 key 재발급 API (ADMIN, PROFESSOR 전용)
    @Operation(summary = "강의 key 재발급", description = "특정 강의의 key를 재발급합니다. (ADMIN, PROFESSOR 전용)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR')")
    @GetMapping("/{courseId}/key")
    fun reissueCourseKey(@PathVariable courseId: Long, authentication: Authentication): ResponseEntity<String> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")
        val newKey = courseService.reissueCourseKey(courseId, email)
        return ResponseEntity.ok(newKey)
    }

    // 강의 추가 (ADMIN, PROFESSOR 전용)
    @Operation(summary = "강의 추가", description = "새로운 강의를 생성합니다. (ADMIN, PROFESSOR 전용)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR')")
    @PostMapping
    fun createCourse(@RequestBody courseDto: CourseDto, authentication: Authentication): ResponseEntity<CourseDto> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(courseService.createCourse(courseDto, email))
    }

    // 강의 수정 (ADMIN, PROFESSOR 전용)
    @Operation(summary = "강의 수정", description = "특정 강의의 정보를 수정합니다. (ADMIN, PROFESSOR 전용)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR')")
    @PutMapping("/{courseId}")
    fun updateCourse(@PathVariable courseId: Long, @RequestBody courseDto: CourseDto, authentication: Authentication): ResponseEntity<CourseDto> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")
        return ResponseEntity.ok(courseService.updateCourse(courseId, courseDto, email))
    }

    // 강의 삭제 (ADMIN 전용)
    @Operation(summary = "강의 삭제", description = "특정 강의를 삭제합니다. (ADMIN 전용)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{courseId}")
    fun deleteCourse(@PathVariable courseId: Long): ResponseEntity<String> {
        courseService.deleteCourse(courseId)
        return ResponseEntity.ok("Course deleted successfully")
    }

    // 강의 종료 (ADMIN 전용)
    @Operation(summary = "강의 종료", description = "강의를 종료합니다. Pod 전체 삭제, JCode 레코드 삭제. (ADMIN 전용)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{courseId}/end")
    fun endCourse(@PathVariable courseId: Long): ResponseEntity<String> {
        courseService.endCourse(courseId)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Course termination requested")
    }

    // 강의 아카이브 (ADMIN 전용)
    @Operation(summary = "강의 아카이브", description = "종료된 강의를 아카이브합니다. NS 삭제. (ADMIN 전용)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{courseId}/archive")
    fun archiveCourse(@PathVariable courseId: Long): ResponseEntity<String> {
        courseService.archiveCourse(courseId)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Course archive requested")
    }

    // 강의 재개설 (ADMIN 전용)
    @Operation(summary = "강의 재개설", description = "종료된 강의를 재개설합니다. NS 재생성. (ADMIN 전용)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{courseId}/reopen")
    fun reopenCourse(@PathVariable courseId: Long): ResponseEntity<String> {
        courseService.reopenCourse(courseId)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Course reopen requested")
    }

    @Operation(summary = "강의 인프라 재시도", description = "실패한 강의 인프라 작업을 다시 요청합니다. (ADMIN 전용)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{courseId}/infrastructure/retry")
    fun retryInfrastructure(@PathVariable courseId: Long): ResponseEntity<String> {
        courseService.retryInfrastructure(courseId)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Course infrastructure retry requested")
    }

    // 강의 상세 정보 조회
    @Operation(
        summary = "강의 상세 정보 조회",
        description = "특정 강의의 상세 정보를 조회합니다."
    )
    @GetMapping("/{courseId}/details")
    fun getCourseDetails(@PathVariable courseId: Long): ResponseEntity<UserCourseDetailsDto> {
        return ResponseEntity.ok(courseService.getCourseDetails(courseId))
    }
}
