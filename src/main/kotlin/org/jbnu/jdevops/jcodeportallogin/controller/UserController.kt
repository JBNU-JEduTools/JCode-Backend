package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.jbnu.jdevops.jcodeportallogin.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import org.jbnu.jdevops.jcodeportallogin.dto.course.CourseJoinDto
import org.jbnu.jdevops.jcodeportallogin.dto.jcode.JCodeDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserInfoDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserProfileUpdateDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCourseDetailsDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCoursesDto
import org.jbnu.jdevops.jcodeportallogin.service.JCodeService
import org.jbnu.jdevops.jcodeportallogin.service.token.JwtAuthService
import org.jbnu.jdevops.jcodeportallogin.service.token.TokenType
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException

@Tag(name = "User API", description = "일반 사용자 관련 API (내 정보, 강의 가입/탈퇴 등)")
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val jCodeService: JCodeService,
    private val jwtAuthService: JwtAuthService
) {
    // 유저 정보 조회
    @Operation(
        summary = "내 정보 조회",
        description = "현재 인증된 사용자의 정보를 조회합니다."
    )
    @GetMapping("/me")
    fun getUserInfo(request: HttpServletRequest, authentication: Authentication): ResponseEntity<UserInfoDto> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        val userInfo = userService.getUserInfo(email)
        return ResponseEntity.ok(userInfo)
    }

    @Operation(
        summary = "내 정보 수정",
        description = "현재 인증된 사용자의 정보를 수정합니다. 이름은 수정 가능하며, 학생번호는 아직 설정되지 않은 경우에만 수정할 수 있습니다."
    )
    @PutMapping("/me")
    fun updateUserInfo(
        @RequestBody updateDto: UserProfileUpdateDto,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        val result = userService.updateUserInfo(email, updateDto)
        return ResponseEntity.ok(result)
    }

    // 유저별 강의 정보 조회
    @Operation(
        summary = "내 강의 정보 조회",
        description = "현재 인증된 사용자가 참가 중인 강의 목록을 조회합니다."
    )
    @GetMapping("/me/courses")
    fun getUserCourses(request: HttpServletRequest, authentication: Authentication): ResponseEntity<List<UserCoursesDto>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        val courses = userService.getUserCourses(email)
        return ResponseEntity.ok(courses)
    }

    // 조교 전용 강의 정보 조회
    @Operation(
        summary = "내 조교 권한 강의 정보 조회",
        description = "현재 인증된 사용자가 조교로서 참가 중인 강의 목록을 조회합니다. (ASSISTANT 전용)"
    )
    @PreAuthorize("hasRole('ASSISTANT')")
    @GetMapping("/me/assistant/courses")
    fun getUserAssistantCourses(request: HttpServletRequest, authentication: Authentication): ResponseEntity<List<UserCoursesDto>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        val courses = userService.getUserAssistantCourses(email)
        return ResponseEntity.ok(courses)
    }

    // 유저별 JCode 정보 조회
    @Operation(
        summary = "내 JCode 정보 조회",
        description = "현재 인증된 사용자가 보유한 JCode 목록을 조회합니다."
    )
    @GetMapping("/me/jcodes")
    fun getUserJcodes(request: HttpServletRequest, authentication: Authentication): ResponseEntity<List<JCodeDto>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        return ResponseEntity.ok(userService.getUserJcodes(email))
    }

    // 유저별 참가 강의의 과제 및 JCode 정보 조회
    @Operation(
        summary = "내 강의 상세 정보 조회",
        description = "현재 인증된 사용자의 강의와 관련된 상세 정보를 조회합니다."
    )
    @GetMapping("/me/courses/details")
    fun getUserCoursesWithDetails(request: HttpServletRequest, authentication: Authentication): ResponseEntity<List<UserCourseDetailsDto>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        return ResponseEntity.ok(userService.getUserCoursesWithDetails(email))
    }

    // 유저 강의 가입
    @Operation(
        summary = "강의 가입",
        description = "현재 인증된 사용자가 요청 본문에 포함된 courseKey를 이용해 가입합니다."
    )
    @PostMapping("/me/courses")
    fun joinCourse(
        @RequestBody joinRequest: CourseJoinDto,
        request: HttpServletRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        // courseId 대신, joinRequest.courseKey를 이용하여 강의를 조회하고 가입 처리
        val courseId = userService.joinCourse(email, joinRequest.courseKey)

        // courseId와 메시지를 Map으로 묶어서 반환
        val response = mapOf(
            "courseId" to courseId,
            "msg" to "Successfully joined the course"
        )
        return ResponseEntity.ok(response)
    }


    // 유저 강의 탈퇴
    @Operation(
        summary = "강의 탈퇴",
        description = "현재 인증된 사용자가 특정 강의에서 탈퇴합니다."
    )
    @DeleteMapping("/me/courses/{courseId}")
    fun leaveCourse(@PathVariable courseId: Long, request: HttpServletRequest, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        userService.leaveCourse(courseId, email)

        // courseId와 메시지를 Map으로 묶어서 반환
        val response = mapOf(
            "courseId" to courseId,
            "msg" to "Successfully left the course"
        )
        return ResponseEntity.ok(response)
    }

    // JCode 삭제
    @Operation(
        summary = "JCode 삭제",
        description = "특정 강의에 대해 자신의 JCode를 삭제합니다."
    )
    @DeleteMapping("/me/courses/{courseId}/jcodes")
    fun deleteMyJCode(
        @PathVariable courseId: Long,
        @RequestHeader("Authorization") authorization: String,
        authentication: Authentication,
        @RequestParam(name = "snapshot", required = false, defaultValue = "false") snapshot: Boolean
    ): ResponseEntity<String> {
        // Authorization 헤더에서 "Bearer " 접두사를 제거하여 토큰만 추출 및 검증
        val token = authorization.removePrefix("Bearer").trim()
        if (!jwtAuthService.validateToken(token, TokenType.ACCESS)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        jCodeService.deleteJCode(email, courseId, token, snapshot)
        return ResponseEntity.ok("JCode deleted successfully")
    }

    //  일반 로그인 jwt 인증 함수

    private fun extractEmailFromRequest(request: HttpServletRequest): String {
        val authHeader = request.getHeader("Authorization")
            ?: throw IllegalArgumentException("Authorization header is missing")

        if (!authHeader.startsWith("Bearer ")) {
            throw IllegalArgumentException("Invalid token format")
        }

        val token = authHeader.substring(7)
        return jwtAuthService.extractEmail(token, TokenType.ACCESS)
    }

    // JWT 토큰에서 이메일 파싱
    private fun extractEmailFromToken(request: HttpServletRequest): String {
        val token = request.getHeader("Authorization")?.removePrefix("Bearer ")
            ?: throw IllegalArgumentException("Missing Authorization Header")

        return jwtAuthService.extractEmail(token, TokenType.ACCESS)
    }
}
