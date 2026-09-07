package org.jbnu.jdevops.jcodeportallogin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebClientConfigTest {
    @Test
    fun `workspace generator supports readiness lookup scope`() {
        assertTrue("jcode:read" in WORKSPACE_GENERATOR_SCOPES)
    }
}
