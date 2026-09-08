package org.jbnu.jdevops.jcodeportallogin.service.token

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

enum class TokenType {
    ACCESS,
    REFRESH
}

@Service
class JwtAuthService(
    @Value("\${jwt.secret}") private val accessSecretKey: String,
    @Value("\${jwt.expire}") private val accessExpireTime: Long,
    @Value("\${jwt.refresh.secret}") private val refreshSecretKey: String,
    @Value("\${jwt.refresh.expire}") private val refreshExpireTime: Long
) {

    fun createToken(email: String, role: RoleType, tokenType: TokenType, assistantCourseIds: List<Long> = emptyList()): String {
        val secretKey = when (tokenType) {
            TokenType.ACCESS -> accessSecretKey
            TokenType.REFRESH -> refreshSecretKey
        }

        val expireTime = when (tokenType) {
            TokenType.ACCESS -> accessExpireTime
            TokenType.REFRESH -> refreshExpireTime
        }

        // 시크릿 키를 바이트 배열로 변환 후 HMAC SHA 키 생성
        val key = Keys.hmacShaKeyFor(secretKey.toByteArray())

        val claims = Jwts.claims().setSubject(email)
        claims["role"] = role
        if (assistantCourseIds.isNotEmpty()) {
            claims["assistantCourses"] = assistantCourseIds
        }

        return Jwts.builder()
            .setClaims(claims)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expireTime))
            .signWith(key)
            .compact()
    }

    // JWT에서 전체 클레임 추출
    fun getClaims(token: String, tokenType: TokenType): Claims {
        val secretKey = when (tokenType) {
            TokenType.ACCESS -> accessSecretKey
            TokenType.REFRESH -> refreshSecretKey
        }

        return Jwts.parserBuilder()
            .setSigningKey(secretKey.toByteArray())
            .build()
            .parseClaimsJws(token)
            .body
    }

    // 이메일 추출
    fun extractEmail(token: String, tokenType: TokenType): String {
        return getClaims(token, tokenType).subject
    }

    // 토큰 검증
    fun validateToken(token: String, tokenType: TokenType): Boolean {
        val secretKey = when (tokenType) {
            TokenType.ACCESS -> accessSecretKey
            TokenType.REFRESH -> refreshSecretKey
        }

        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(secretKey.toByteArray())
                .build()
                .parseClaimsJws(token)

            !claims.body.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }
}
