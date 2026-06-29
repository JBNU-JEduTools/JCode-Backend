package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
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
    fun deleteAssignment(@PathVariable courseId: Long, @PathVariable assignmentId: Long, authentication: Authentication): ResponseEntity<String> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        assignmentService.deleteAssignment(courseId, assignmentId, email)
        return ResponseEntity.ok("Assignment deleted successfully")
    }

    // 스타터 코드 업로드
    @Operation(summary = "스타터 코드 업로드", description = "과제에 스타터 코드(zip)를 업로드합니다.")
    @PostMapping("/{assignmentId}/starter-code", consumes = ["multipart/form-data"])
    fun uploadStarterCode(
        @PathVariable courseId: Long,
        @PathVariable assignmentId: Long,
        @RequestParam("file") file: MultipartFile,
        @RequestHeader("Authorization") authorization: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val email = authentication.principal as? String
            ?: throw IllegalStateException("인증 정보를 찾을 수 없습니다.")
        val token = authorization.removePrefix("Bearer").trim()
        assignmentService.uploadStarterCode(courseId, assignmentId, file, email, token)
        return ResponseEntity.ok(mapOf("msg" to "스타터 코드가 업로드되었습니다."))
    }
}
