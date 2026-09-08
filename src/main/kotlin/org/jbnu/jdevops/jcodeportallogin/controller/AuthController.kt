package org.jbnu.jdevops.jcodeportallogin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.jbnu.jdevops.jcodeportallogin.dto.auth.LoginUserDto
import org.jbnu.jdevops.jcodeportallogin.dto.auth.RegisterUserDto
import org.jbnu.jdevops.jcodeportallogin.service.*
import org.jbnu.jdevops.jcodeportallogin.util.JwtUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Auth API", description = "인증 및 회원가입 관련 API")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val jwtUtil: JwtUtil
) {
    // 일반 회원가입
    @Operation(summary = "일반 회원가입", description = "사용자가 일반 회원가입을 요청합니다.")
    @PostMapping("/signup")
    fun register(@RequestBody registerUserDto: RegisterUserDto): ResponseEntity<String> {
        return userService.register(registerUserDto)
    }

    // 일반 로그인 ( ADMIN, PROFESSOR, ASSISTANT )
    @Operation(
        summary = "일반 로그인",
        description = "ADMIN, PROFESSOR, ASSISTANT 역할의 사용자가 일반 로그인을 수행합니다. 인증 후 JWT 토큰이 발급됩니다."
    )
    @PostMapping("/login/basic")
    fun basicLogin(@RequestBody loginUserDto: LoginUserDto, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        val result = authService.basicLogin(loginUserDto)
        response.addCookie(jwtUtil.createJwtCookie("jwt_auth", result["token"] ?: ""))
        return ResponseEntity.ok(result)
    }

    @PostMapping("/token")
    @Operation(
        summary = "Access Token 발급",
        description = "클라이언트의 요청에 기반하여 서버의 세션 또는 쿠키 정보를 활용해 Access Token을 발급합니다. 발급된 토큰은 응답 헤더의 'Authorization'에 'Bearer {token}' 형식으로 포함되며, CORS 설정에 따라 클라이언트에서 접근할 수 있도록 'Access-Control-Expose-Headers' 헤더가 추가됩니다."
    )
    fun getAccessToken(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        return try {
            val tokens = authService.getAccessToken(request)
            response.setHeader("Authorization", "Bearer ${tokens["accessToken"]}")
            response.setHeader("Access-Control-Expose-Headers", "Authorization") // Authorization 헤더 클라이언트에 노출

            response.addCookie(jwtUtil.createJwtCookie("jcodeRt", tokens["refreshToken"]!!))
            ResponseEntity.ok(mapOf("message" to "Token created"))
        } catch (_: Exception) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "인증을 갱신할 수 없습니다."))
        }
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "JWT 토큰 리프레시",
        description = "HTTP 요청에 포함된 refresh token 쿠키를 검증하여, 유효할 경우 새로운 Access 토큰(응답 헤더)과 Refresh 토큰(쿠키)을 발급합니다."
    )
    fun refreshToken(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        return try {
            val tokens = authService.refreshTokens(request)
            response.setHeader("Authorization", "Bearer ${tokens["accessToken"]}")
            response.setHeader("Access-Control-Expose-Headers", "Authorization") // Authorization 헤더 클라이언트에 노출

            response.addCookie(jwtUtil.createJwtCookie("jcodeRt", tokens["refreshToken"]!!))
            ResponseEntity.ok(mapOf("message" to "Tokens refreshed"))
        } catch (_: Exception) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "인증을 갱신할 수 없습니다."))
        }
    }
}
