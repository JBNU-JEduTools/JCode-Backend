package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.jbnu.jdevops.jcodeportallogin.dto.jcode.JCodeDto
import org.jbnu.jdevops.jcodeportallogin.dto.jcode.JCodeMainRequestDto
import org.jbnu.jdevops.jcodeportallogin.service.JCodeService
import org.jbnu.jdevops.jcodeportallogin.service.token.JwtAuthService
import org.jbnu.jdevops.jcodeportallogin.service.token.TokenType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@Tag(name = "JCode Admin API", description = "JCode 관리 API (feat. 관리자)")
@RestController
@RequestMapping("/api/courses/{courseId}/jcodes")
class JCodeController(
    private val jCodeService: JCodeService,
    private val jwtAuthService: JwtAuthService
) {
    // JCode 추가
    @Operation(
        summary = "JCode 추가",
        description = "관리자가 특정 강의에 대해 JCode를 생성 및 할당합니다."
    )
    @PostMapping
    fun createJCode(
        @RequestBody jcodeMainRequestDto: JCodeMainRequestDto,
        @PathVariable courseId: Long,
        @RequestHeader("Authorization") authorization: String,
        authentication: Authentication
    ): ResponseEntity<JCodeDto> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        // Authorization 헤더에서 "Bearer " 접두사를 제거하여 토큰만 추출 및 검증
        val token = authorization.removePrefix("Bearer").trim()
        if (!jwtAuthService.validateToken(token, TokenType.ACCESS)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jCodeService.createJCode(
            courseId,
            jcodeMainRequestDto.userEmail,
            email,
            token,
            jcodeMainRequestDto.snapshot,
            jcodeMainRequestDto.assignmentId
        ))
    }

    // JCode 삭제 (관리자 전용)
    @Operation(
        summary = "JCode 삭제",
        description = "관리자가 특정 강의에 대해 해당 사용자의 JCode를 삭제합니다."
    )
    @PreAuthorize("hasRole('ADMIN')") // ADMIN 권한이 없는 사용자는 모두 접근 불가
    @DeleteMapping
    fun deleteJCode(
        @RequestBody jcodeMainRequestDto: JCodeMainRequestDto,
        @PathVariable courseId: Long,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<String> {
        // Authorization 헤더에서 "Bearer " 접두사를 제거하여 토큰만 추출 및 검증
        val token = authorization.removePrefix("Bearer").trim()
        if (!jwtAuthService.validateToken(token, TokenType.ACCESS)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        jCodeService.deleteJCode(
            jcodeMainRequestDto.userEmail,
            courseId,
            token,
            jcodeMainRequestDto.snapshot,
            jcodeMainRequestDto.assignmentId
        )
        return ResponseEntity.ok("JCode deleted successfully")
    }

    @PostMapping("/retry")
    fun retryJCode(
        @RequestBody request: JCodeMainRequestDto,
        @PathVariable courseId: Long,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")
        jCodeService.retryJCode(email, request.userEmail, courseId, request.snapshot, request.assignmentId)
        return ResponseEntity.accepted().body(mapOf("msg" to "JCode 작업 재시도를 요청했습니다."))
    }
}
