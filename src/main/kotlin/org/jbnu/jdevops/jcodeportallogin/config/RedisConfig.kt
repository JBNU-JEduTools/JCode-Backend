package org.jbnu.jdevops.jcodeportallogin.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisNode
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import java.time.Duration

@Configuration
@EnableRedisHttpSession
@EnableRedisRepositories(basePackages = ["org.jbnu.jdevops.jcodeportallogin.repo.redis"]) // Redis 전용 리포지토리만 사용
class RedisConfig(
    @Value("\${redis.host}") private val redisHost: String,
    @Value("\${redis.port}") private val redisPort: Int,
    @Value("\${redis.password}") private val redisPassword: String,
    @Value("\${redis.sentinel.master:}") private val redisSentinelMaster: String,
    @Value("\${redis.sentinel.nodes:}") private val redisSentinelNodes: String,
    @Value("\${redis.command.timeout-millis:3000}") private val redisCommandTimeoutMillis: Long,
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val password = RedisPassword.of(redisPassword)
        val sentinelNodes = redisSentinelNodes.split(',').map(String::trim).filter(String::isNotEmpty)

        if (redisSentinelMaster.isNotBlank() || sentinelNodes.isNotEmpty()) {
            require(redisSentinelMaster.isNotBlank() && sentinelNodes.isNotEmpty()) {
                "redis.sentinel.master and redis.sentinel.nodes must be configured together"
            }
            val redisConfig = RedisSentinelConfiguration().master(redisSentinelMaster)
            sentinelNodes.forEach { redisConfig.addSentinel(RedisNode.fromString(it)) }
            if (redisPassword.isNotBlank()) {
                redisConfig.setPassword(password)
                redisConfig.setSentinelPassword(password)
            }
            return LettuceConnectionFactory(redisConfig, clientConfiguration())
        }

        val redisConfig = RedisStandaloneConfiguration(redisHost, redisPort)
        if (redisPassword.isNotBlank()) {
            redisConfig.setPassword(password)
        }
        return LettuceConnectionFactory(redisConfig, clientConfiguration())
    }

    private fun clientConfiguration(): LettuceClientConfiguration {
        require(redisCommandTimeoutMillis > 0) { "redis.command.timeout-millis must be positive" }
        return LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(redisCommandTimeoutMillis))
            .build()
    }

    @Bean
    fun redisTemplate(): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            connectionFactory = redisConnectionFactory()
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer()
        }
    }

}
