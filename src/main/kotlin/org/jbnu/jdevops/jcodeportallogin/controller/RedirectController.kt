package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.jbnu.jdevops.jcodeportallogin.dto.jcode.RedirectDto
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.JCodeRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.service.RedisService
import org.jbnu.jdevops.jcodeportallogin.util.AuthorizationUtil
import org.jbnu.jdevops.jcodeportallogin.util.JwtUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Tag(name = "Redirect API", description = "JCode(Node.js 서버)로의 리다이렉션 관련 API")
@RestController
@RequestMapping("/api/redirect")
class RedirectController(
    private val jwtUtil: JwtUtil,
    private val redisService: RedisService,
    private val jCodeRepository: JCodeRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val assignmentRepository: AssignmentRepository,
    private val userCoursesRepository: UserCoursesRepository
) {

    @Value("\${router.url}")  // 환경 변수에서 Node.js URL 가져오기
    private lateinit var routerUrl: String

    // Node.js 서버로 리다이렉션 (JCode)
    @Operation(
        summary = "JCode(Node.js 서버) 리다이렉션",
        description = "사용자 정보를 Redis에 저장 후 UUID를 파라미터로 전달하여 Node.js 서버에 리다이렉트 합니다."
    )
    @PostMapping
    fun redirectToNode(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
        @RequestBody redirectRequest: RedirectDto
    ): ResponseEntity<Map<String, String>> {

        val token = request.getHeader("Authorization")?.removePrefix("Bearer ")
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization Token")

        val currentEmail = authentication.principal as? String
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email in authentication")
        val currentUser = userRepository.findByEmail(currentEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
        val user = userRepository.findByEmail(redirectRequest.userEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val course = courseRepository.findById(redirectRequest.courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "종료되거나 아카이브된 강의에는 진입할 수 없습니다.")
        }
        if (!userCoursesRepository.existsByUserIdAndCourseId(user.id, course.id)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "대상 사용자가 해당 강의에 소속되어 있지 않습니다.")
        }
        AuthorizationUtil.validateUserAuthority(
            currentUser.role,
            currentUser.id,
            user.id,
            course.id,
            userCoursesRepository
        )

        val storedJcode = jCodeRepository.findByUserIdAndCourseIdAndSnapshot(user.id, course.id, redirectRequest.snapshot)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "활성 JCode가 없습니다.")

        // 사용자 프로필 정보를 Redis에 저장하고 UUID를 획득 & UUID를 UTF-8로 URL 인코딩
        val uuid = redisService.storeUserProfile(user.email, user.studentNum.toString(), course.code, course.clss.toString(), redirectRequest.snapshot.toString())
        val encodedUUID = URLEncoder.encode(uuid, StandardCharsets.UTF_8.toString()).replace("+", "%2B")

        // 학생 Jcode 정보 Redis 동기화
        if (redirectRequest.snapshot) redisService.storeSnapshotUserCourse(user.email, course.code, course.clss, storedJcode.jcodeUrl)
        else redisService.storeUserCourse(user.email, course.code, course.clss, storedJcode.jcodeUrl)

        // 과제별 폴더 경로 결정
        val folderPath = if (redirectRequest.assignmentId != null) {
            val assignment = assignmentRepository.findByIdAndCourseId(redirectRequest.assignmentId, course.id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found in course") }
            "/home/coder/project/${assignment.dirName}"
        } else {
            "/home/coder/project"
        }

        // Node.js 서버 URL에 인코딩된 UUID 파라미터만 포함하여 구성
        val encodedFolder = URLEncoder.encode(folderPath, StandardCharsets.UTF_8.toString())
        val finalNodeJsUrl = "$routerUrl?id=$encodedUUID&folder=$encodedFolder"

        // Keycloak Access Token을 HTTP-Only Secure 쿠키로 설정
        response.addCookie(jwtUtil.createJwtCookie("jcodeAt", token))

        // SPA에서 사용할 수 있도록 JSON으로 URL 반환
        return ResponseEntity.ok(mapOf("url" to finalNodeJsUrl))
    }
}
