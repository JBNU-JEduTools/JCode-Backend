package org.jbnu.jdevops.jcodeportallogin.security

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.service.RedisService
import org.jbnu.jdevops.jcodeportallogin.service.token.JwtAuthService
import org.jbnu.jdevops.jcodeportallogin.service.token.KeycloakAuthService
import org.jbnu.jdevops.jcodeportallogin.service.token.TokenType
import org.jbnu.jdevops.jcodeportallogin.util.JwtUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class CustomAuthenticationSuccessHandler(
    private val keycloakAuthService: KeycloakAuthService,
    private val jwtAuthService: JwtAuthService,
    private val authorizedClientService: OAuth2AuthorizedClientService,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository,
    private val redisService: RedisService,
    @Value("\${front.domain}") private val frontDomain: String,
    private val jwtUtil: JwtUtil,
) : AuthenticationSuccessHandler {

    @Throws(IOException::class, ServletException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: org.springframework.security.core.Authentication
    ) {
        // 1. OAuth2AuthenticationToken으로 캐스팅 후, Keycloak access token 획득
        val oauth2Auth = authentication as OAuth2AuthenticationToken
        val authorizedClient = authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(
            oauth2Auth.authorizedClientRegistrationId,
            oauth2Auth.name
        )
        val keycloakAccessToken = authorizedClient?.accessToken?.tokenValue

        if (keycloakAccessToken.isNullOrEmpty() || !keycloakAuthService.validateToken(keycloakAccessToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing Keycloak token")
            return
        }

        // 2. Keycloak 토큰에서 클레임 추출 및 이메일 가져오기
        val claims = keycloakAuthService.extractClaims(keycloakAccessToken)
        val email = claims["email"] as? String
            ?: claims["preferred_username"] as? String
            ?: claims["sub"] as? String

        if (email.isNullOrEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing email in Keycloak token")
            return
        }

        // 3. DB에서 사용자 조회; 없으면 회원가입 (기본 STUDENT 역할 부여)
        val user: User = userRepository.findByEmail(email) ?: userRepository.save(
            User(email = email, role = RoleType.STUDENT)
        )
        val role: RoleType = user.role

        // 4. OIDC 사용자라면 id_token 추출하여 Redis 해시("user:id_tokens")에 저장
        val oidcUser = oauth2Auth.principal as? OidcUser
        val idTokenValue: String? = oidcUser?.idToken?.tokenValue
        if (idTokenValue != null) {
            redisService.storeIdToken(email, idTokenValue)
        }

        // 5. 조교 수업 목록 조회 및 자체 발급 refresh token 생성
        val assistantCourseIds = userCoursesRepository.findByUserEmailAndRole(email, RoleType.ASSISTANT)
            .map { it.course.id }
        val myRefreshToken = jwtAuthService.createToken(email, role, TokenType.REFRESH, assistantCourseIds)
        redisService.storeRefreshToken(email, myRefreshToken)
        // refresh token을 HttpOnly 쿠키로 전달
        response.addCookie(jwtUtil.createJwtCookie("jcodeRt", myRefreshToken))

        // 6. 자체 JWT 토큰(Access token) 발급 및 전달은 /api/auth/token로 수행

        // 7. SecurityContext에 인증 정보 저장 (옵션)
        val authorities = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        val authToken = UsernamePasswordAuthenticationToken(email, null, authorities)
        SecurityContextHolder.getContext().authentication = authToken

        // 8. 로그인 성공 후 프론트엔드 URL로 리다이렉트
        response.sendRedirect("$frontDomain/login/success")
    }
}
