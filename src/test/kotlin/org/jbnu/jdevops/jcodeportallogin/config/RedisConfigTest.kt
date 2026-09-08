package org.jbnu.jdevops.jcodeportallogin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

class RedisConfigTest {
    @Test
    fun `uses standalone Redis when Sentinel is not configured`() {
        val factory = RedisConfig("redis", 6379, "secret", "", "", 3000)
            .redisConnectionFactory() as LettuceConnectionFactory

        assertNotNull(factory.standaloneConfiguration)
        assertEquals("redis", factory.standaloneConfiguration.hostName)
        assertEquals(6379, factory.standaloneConfiguration.port)
        assertEquals(3000, factory.clientConfiguration.commandTimeout.toMillis())
    }

    @Test
    fun `uses all Sentinel seeds and authenticates both connections`() {
        val factory = RedisConfig(
            "unused",
            6379,
            "secret",
            "jcode",
            "sentinel-0:26379, sentinel-1:26379, sentinel-2:26379",
            3000,
        ).redisConnectionFactory() as LettuceConnectionFactory

        val sentinel = requireNotNull(factory.sentinelConfiguration)
        assertEquals("jcode", requireNotNull(sentinel.master).name)
        assertEquals(3, sentinel.sentinels.size)
        assertEquals(setOf("sentinel-0", "sentinel-1", "sentinel-2"), sentinel.sentinels.map { it.host }.toSet())
        assertEquals(true, sentinel.password.isPresent)
        assertEquals(true, sentinel.sentinelPassword.isPresent)
    }

    @Test
    fun `rejects incomplete Sentinel configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            RedisConfig("redis", 6379, "secret", "jcode", "", 3000).redisConnectionFactory()
        }
    }

    @Test
    fun `rejects a non-positive command timeout`() {
        assertThrows(IllegalArgumentException::class.java) {
            RedisConfig("redis", 6379, "secret", "", "", 0).redisConnectionFactory()
        }
    }
}
