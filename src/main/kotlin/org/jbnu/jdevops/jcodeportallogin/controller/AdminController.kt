package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserInfoDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserRoleChangeDto
import org.jbnu.jdevops.jcodeportallogin.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@Tag(name = "Admin API", description = "관리자 전용 사용자 관리 API")
@RestController
@RequestMapping("/api/users")
class AdminController(private val userService: UserService) {
    // 모든 유저 조회 (ADMIN 전용)
    @Operation(
        summary = "모든 유저 조회",
        description = "모든 사용자 정보를 조회합니다."
    )
    @PreAuthorize("hasRole('ADMIN')") // ADMIN 권한이 없는 사용자는 모두 접근 불가
    @GetMapping
    fun getAllUsers(): List<UserInfoDto> {
        return userService.getAllUsers()
    }

    // 특정 유저 정보 조회 (ADMIN 전용)
    @Operation(summary = "특정 유저 정보 조회", description = "관리자가 특정 사용자의 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')") // ADMIN 권한이 없는 사용자는 모두 접근 불가
    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: Long): ResponseEntity<UserDto> {
        return userService.getUserById(userId)?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.notFound().build()
    }

    // 특정 유저 권한 변경 (ADMIN, PROFESSOR 전용)
    @Operation(summary = "특정 유저 권한 변경", description = "관리자(or 교수)가 특정 사용자의 권한을 변경합니다.")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR')")
    @PutMapping("/{userId}/role")
    fun updateUserRole(
        @PathVariable userId: Long,
        @RequestBody request: UserRoleChangeDto,
        authentication: Authentication
    ): ResponseEntity<Unit> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        userService.updateUserRole(email, userId, request.newRole, request.courseId)
        return ResponseEntity.ok().build()
    }

    // 특정 유저 삭제 (ADMIN 전용)
    @Operation(summary = "특정 유저 삭제", description = "관리자가 특정 사용자를 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')") // ADMIN 권한이 없는 사용자는 모두 접근 불가
    @DeleteMapping("/{userId}")
    fun deleteUser(@PathVariable userId: Long): ResponseEntity<Unit> {
        userService.deleteUser(userId)
        return ResponseEntity.ok().build()
    }

    // 특정 유저 강의 탈퇴 (ADMIN, PROFESSOR 전용)
    @Operation(summary = "특정 유저 강의 탈퇴", description = "관리자(or 교수)가 특정 사용자를 특정 강의에서 탈퇴시킵니다.")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR')")
    @DeleteMapping("/{userId}/courses/{courseId}")
    fun chaseOutCourse(@PathVariable userId: Long, @PathVariable courseId: Long, request: HttpServletRequest, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        val token = request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization token is required")
        userService.chaseOutCourse(userId, courseId, email, token)

        // courseId와 메시지를 Map으로 묶어서 반환
        val response = mapOf(
            "userId" to userId,
            "courseId" to courseId,
            "msg" to "Successfully left the course"
        )
        return ResponseEntity.ok(response)
    }
}
