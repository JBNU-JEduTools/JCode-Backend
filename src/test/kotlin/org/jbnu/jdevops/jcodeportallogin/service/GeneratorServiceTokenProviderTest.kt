package org.jbnu.jdevops.jcodeportallogin.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class GeneratorServiceTokenProviderTest {
    private val secret = "0123456789abcdef0123456789abcdef"

    @Test
    fun `service token is short lived and operation scoped`() {
        val provider = GeneratorServiceTokenProvider(
            secret = secret,
            issuer = "jcode-backend",
            audience = "jcode-generator",
            subject = "jcode-backend"
        )

        val claims = Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)))
            .requireIssuer("jcode-backend")
            .requireAudience("jcode-generator")
            .build()
            .parseClaimsJws(provider.createToken(setOf("jcode:write")))
            .body

        assertEquals("jcode-backend", claims.subject)
        assertEquals("jcode:write", claims["scope"])
        assertEquals("jcode-", claims["namespace_prefix"])
        assertTrue(claims.expiration.time - claims.issuedAt.time <= 60_000)
    }
}
