package org.jbnu.jdevops.jcodeportallogin.controller.watcher

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.jbnu.jdevops.jcodeportallogin.dto.watcher.SubmissionDashboardDto
import org.jbnu.jdevops.jcodeportallogin.service.watcher.SubmissionDashboardService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@Tag(name = "Submission Dashboard API", description = "과제 제출 현황 대시보드 (교수/조교 전용)")
@RestController
@RequestMapping("/api/watcher/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR', 'STUDENT')")
class SubmissionDashboardController(
    private val dashboardService: SubmissionDashboardService
) {
    @Operation(
        summary = "과제 제출 현황 대시보드",
        description = "특정 과제의 학생별 제출 현황 및 의심 플래그를 조회합니다. 교수, 관리자, 해당 과목 조교만 접근 가능."
    )
    @GetMapping("/courses/{courseId}/assignments/{assignmentId}")
    fun getDashboard(
        @PathVariable courseId: Long,
        @PathVariable assignmentId: Long,
        authentication: Authentication
    ): ResponseEntity<SubmissionDashboardDto> {
        val email = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")

        val result = dashboardService.getDashboard(courseId, assignmentId, email)
        return ResponseEntity.ok(result)
    }
}
