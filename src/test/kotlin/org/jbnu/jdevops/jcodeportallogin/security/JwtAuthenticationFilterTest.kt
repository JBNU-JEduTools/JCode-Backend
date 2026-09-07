package org.jbnu.jdevops.jcodeportallogin.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JwtAuthenticationFilterTest {
    @Test
    fun `kubernetes health groups do not require a JWT`() {
        assertTrue(isJwtAuthenticationExcluded("/actuator/health/liveness"))
        assertTrue(isJwtAuthenticationExcluded("/actuator/health/readiness"))
    }

    @Test
    fun `application APIs still require a JWT`() {
        assertFalse(isJwtAuthenticationExcluded("/api/courses"))
    }
}
