package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.entity.StarterOverwritePolicy
import org.jbnu.jdevops.jcodeportallogin.service.AssignmentService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Assignment API", description = "강의별 과제 관련 CRUD API (ADMIN, PROFESSOR, 수업별 조교)")
@RestController
@RequestMapping("/api/courses/{courseId}/assignments")
@PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR', 'STUDENT')")
class AssignmentController(
    private val assignmentService: AssignmentService
) {
    // 과제 추가
    @Operation(summary = "과제 추가", description = "특정 강의에 새로운 과제를 추가합니다.")
    @PostMapping
    fun createAssignment(
        @PathVariable courseId: Long,
        @RequestBody assignmentDto: AssignmentDto,
        @RequestHeader("Authorization") authorization: String,
        authentication: Authentication
    ): ResponseEntity<AssignmentDto> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        val token = authorization.removePrefix("Bearer").trim()
        return ResponseEntity.ok(assignmentService.createAssignment(courseId, assignmentDto, email, token))
    }

    // 과제 수정
    @Operation(summary = "과제 수정", description = "특정 강의의 특정 과제를 수정합니다.")
    @PutMapping("/{assignmentId}")
    fun updateAssignment(@PathVariable courseId: Long, @PathVariable assignmentId: Long, @RequestBody assignmentDto: AssignmentDto, authentication: Authentication): ResponseEntity<AssignmentDto> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        return ResponseEntity.ok(assignmentService.updateAssignment(courseId, assignmentId, assignmentDto, email))
    }

    // 과제 삭제
    @Operation(summary = "과제 삭제", description = "특정 강의의 특정 과제를 삭제합니다.")
    @DeleteMapping("/{assignmentId}")
    fun deleteAssignment(
        @PathVariable courseId: Long,
        @PathVariable assignmentId: Long,
        @RequestParam(defaultValue = "90") retentionDays: Int,
        authentication: Authentication
    ): ResponseEntity<String> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        assignmentService.deleteAssignment(courseId, assignmentId, email, retentionDays)
        return ResponseEntity.accepted().body("Assignment archive requested")
    }

    // 스타터 코드 업로드
    @Operation(summary = "스타터 코드 업로드", description = "과제에 스타터 코드(zip)를 업로드합니다.")
    @PostMapping("/{assignmentId}/starter-code", consumes = ["multipart/form-data"])
    fun uploadStarterCode(
        @PathVariable courseId: Long,
        @PathVariable assignmentId: Long,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(defaultValue = "PRESERVE_EXISTING") overwritePolicy: StarterOverwritePolicy,
        @RequestParam(defaultValue = "true") deployNow: Boolean,
        @RequestHeader("Authorization") authorization: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        val token = authorization.removePrefix("Bearer").trim()
        val assignment = assignmentService.uploadStarterCode(
            courseId, assignmentId, file, overwritePolicy, deployNow, email, token
        )
        return ResponseEntity.accepted().body(
            mapOf("msg" to "스타터 코드 원본을 저장했습니다.", "version" to assignment.starterVersion.toString())
        )
    }

    data class ReopenAssignmentRequest(val deadlineDate: java.time.LocalDateTime)

    @PostMapping("/{assignmentId}/reopen")
    fun reopenAssignment(
        @PathVariable courseId: Long,
        @PathVariable assignmentId: Long,
        @RequestBody request: ReopenAssignmentRequest,
        authentication: Authentication
    ): ResponseEntity<AssignmentDto> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        return ResponseEntity.ok(assignmentService.reopenAssignment(courseId, assignmentId, request.deadlineDate, email))
    }

    @PostMapping("/{assignmentId}/retry")
    fun retryAssignment(
        @PathVariable courseId: Long,
        @PathVariable assignmentId: Long,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        assignmentService.retryAssignment(courseId, assignmentId, email)
        return ResponseEntity.accepted().body(mapOf("msg" to "과제 작업 재시도를 요청했습니다."))
    }
}
