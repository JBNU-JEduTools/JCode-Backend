package org.jbnu.jdevops.jcodeportallogin.service

import jakarta.servlet.http.HttpServletRequest
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.repo.LoginRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.service.token.JwtAuthService
import org.jbnu.jdevops.jcodeportallogin.service.token.TokenType
import org.jbnu.jdevops.jcodeportallogin.util.JwtUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceRefreshTest {
    private val userRepository = mock(UserRepository::class.java)
    private val loginRepository = mock(LoginRepository::class.java)
    private val userCoursesRepository = mock(UserCoursesRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val jwtAuthService = mock(JwtAuthService::class.java)
    private val redisService = mock(RedisService::class.java)
    private val jwtUtil = mock(JwtUtil::class.java)
    private val service = AuthService(
        userRepository, loginRepository, userCoursesRepository, passwordEncoder,
        jwtAuthService, redisService, jwtUtil
    )

    @Test
    fun `refresh returns the canonical token selected by Redis CAS`() {
        val request = mock(HttpServletRequest::class.java)
        val user = User(id = 1, email = "student@example.com", role = RoleType.STUDENT)
        `when`(jwtUtil.extractCookieToken(request, "jcodeRt")).thenReturn("old-refresh")
        `when`(jwtAuthService.extractEmail("old-refresh", TokenType.REFRESH)).thenReturn(user.email)
        `when`(jwtAuthService.validateToken("old-refresh", TokenType.REFRESH)).thenReturn(true)
        `when`(userRepository.findByEmail(user.email)).thenReturn(user)
        `when`(userCoursesRepository.findByUserEmailAndRole(user.email, RoleType.ASSISTANT)).thenReturn(emptyList())
        `when`(jwtAuthService.createToken(user.email, RoleType.STUDENT, TokenType.ACCESS, emptyList()))
            .thenReturn("new-access")
        `when`(jwtAuthService.createToken(user.email, RoleType.STUDENT, TokenType.REFRESH, emptyList()))
            .thenReturn("candidate-refresh")
        `when`(redisService.rotateRefreshToken(user.email, "old-refresh", "candidate-refresh"))
            .thenReturn("canonical-refresh")

        val result = service.refreshTokens(request)

        assertEquals("new-access", result["accessToken"])
        assertEquals("canonical-refresh", result["refreshToken"])
        verify(redisService).rotateRefreshToken(user.email, "old-refresh", "candidate-refresh")
    }
}
