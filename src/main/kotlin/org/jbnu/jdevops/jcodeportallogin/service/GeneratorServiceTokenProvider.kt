package org.jbnu.jdevops.jcodeportallogin.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

@Component
class GeneratorServiceTokenProvider(
    @Value("\${generator.auth.secret}") private val secret: String,
    @Value("\${generator.auth.issuer:jcode-backend}") private val issuer: String,
    @Value("\${generator.auth.audience:jcode-generator}") private val audience: String,
    @Value("\${generator.auth.subject:jcode-backend}") private val subject: String
) {
    init {
        require(secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "generator.auth.secret must be at least 32 bytes"
        }
    }

    fun createToken(scopes: Set<String>): String {
        val now = Instant.now()
        val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
        return Jwts.builder()
            .setIssuer(issuer)
            .setAudience(audience)
            .setSubject(subject)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(60)))
            .claim("scope", scopes.sorted().joinToString(" "))
            .claim("namespace_prefix", "jcode-")
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }
}
