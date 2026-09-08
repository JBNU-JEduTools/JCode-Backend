package org.jbnu.jdevops.jcodeportallogin.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import io.jsonwebtoken.Claims
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.service.token.JwtAuthService
import org.jbnu.jdevops.jcodeportallogin.service.token.TokenType
import org.jbnu.jdevops.jcodeportallogin.util.JwtUtil
import org.jbnu.jdevops.jcodeportallogin.service.RedisService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

private val excludedJwtAuthenticationPaths = listOf(
    "/api/auth/token", "/api/auth/refresh",
    "/swagger-ui/**", "/v3/api-docs/**",
    "/oauth2/**", "/login",
    "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
)

internal fun isJwtAuthenticationExcluded(requestUri: String): Boolean {
    val matcher = AntPathMatcher()
    return excludedJwtAuthenticationPaths.any { matcher.match(it, requestUri) }
}

@Component
class JwtAuthenticationFilter(
    private val jwtAuthService: JwtAuthService,
    private val jwtUtil: JwtUtil,
    @Value("\${front.domain}") private val frontDomain: String, // 프론트엔드 도메인
    private val userRepository: UserRepository,
    private val redisService: RedisService,
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        logger.debug("Processing request: ${request.method} ${request.requestURI}")

        // "Authorization" 헤더에서 "Bearer {token}" 형식으로 Access Token 추출
        val accessToken = jwtUtil.extractBearerToken(request)

        if (!accessToken.isNullOrEmpty() &&
            jwtAuthService.validateToken(accessToken, TokenType.ACCESS) &&
            !redisService.isJwtBlacklisted(accessToken)
        ) {
            // access token이 유효한지 확인
            val claims: Claims = jwtAuthService.getClaims(accessToken, TokenType.ACCESS)
            val email = claims.subject
            val tokenRole = RoleType.valueOf(claims["role"].toString())
            logger.debug("Access token validated for user: $email with role: $tokenRole")

            // DB에 저장된 사용자 확인
            val dbUser = userRepository.findByEmail(email)
            if (dbUser == null) {
                logger.warn("User not found in DB: $email")
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found")
                return
            }

            // DB role 기준으로 SecurityContext 설정 (JWT의 role이 아닌 DB의 실제 role 사용)
            val authorities = listOf(SimpleGrantedAuthority("ROLE_${dbUser.role}"))
            val auth = UsernamePasswordAuthenticationToken(email, null, authorities)
            SecurityContextHolder.getContext().authentication = auth
        } else {
            logger.warn("Invalid or missing access token, returning 401 Unauthorized")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing access token")
            return
        }

        filterChain.doFilter(request, response)
    }

    // access token 인증을 제외할 엔드포인트 설정
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val shouldNotFilter = isJwtAuthenticationExcluded(request.requestURI)
        if (shouldNotFilter) {
            logger.debug("Request ${request.requestURI} is excluded from filtering")
        }
        return shouldNotFilter
    }
}
