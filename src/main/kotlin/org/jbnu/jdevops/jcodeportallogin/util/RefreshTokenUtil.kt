package org.jbnu.jdevops.jcodeportallogin.util

import org.jbnu.jdevops.jcodeportallogin.service.RedisService
import org.jbnu.jdevops.jcodeportallogin.service.token.JwtAuthService
import org.jbnu.jdevops.jcodeportallogin.service.token.TokenType
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

// 검증: refresh token 유효성, 블랙리스트, Redis에 저장된 값 일치 여부 확인
object RefreshTokenUtil {
    fun validate(
        refreshToken: String,
        jwtAuthService: JwtAuthService,
        redisService: RedisService
    ) {
        // 1. refresh token 유효성 검증
        if (!jwtAuthService.validateToken(refreshToken, TokenType.REFRESH)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalid or expired")
        }
        // 2. 블랙리스트 체크
        if (redisService.isJwtBlacklisted(refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token blacklisted")
        }
        // Redis의 현재 토큰 비교와 교체는 AuthService의 Lua CAS에서 한 번에 수행한다.
    }
}
