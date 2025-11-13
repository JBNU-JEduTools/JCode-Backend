package org.jbnu.jdevops.jcodeportallogin.util.secret

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.ObjectInputStream

@SpringBootApplication(exclude = [org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration::class])
class RedisSessionParserApplication : CommandLineRunner {

    @Autowired
    lateinit var stringRedisTemplate: StringRedisTemplate

    override fun run(vararg args: String?) {
        val connection = stringRedisTemplate.connectionFactory?.connection

        if (args.isNotEmpty() && !args[0].isNullOrBlank()) {
            // 인수가 있으면 특정 세션 ID 조회
            val sessionId = args[0]!!
            val key = "spring:session:sessions:$sessionId"
            val rawMap = connection?.hGetAll(key.toByteArray())
            if (rawMap != null && rawMap.isNotEmpty()) {
                println("세션 ID: $sessionId")
                println("세션 Hash 데이터:")
                rawMap.forEach { (field, value) ->
                    val deserializedValue = try {
                        SerializationUtils.deserialize(value)
                    } catch (e: Exception) {
                        // 역직렬화 실패 시 기본 문자열로 변환
                        String(value)
                    }
                    println("  ${String(field)} : $deserializedValue")
                }
            } else {
                println("세션 ID $sessionId 에 해당하는 데이터가 없습니다.")
            }
        } else {
            // 인수가 없으면 모든 세션 키 조회
            val sessionKeys = stringRedisTemplate.keys("spring:session:sessions:*")
            if (!sessionKeys.isNullOrEmpty()) {
                println("총 ${sessionKeys.size}개의 세션 키를 찾았습니다.")
                for (key in sessionKeys) {
                    val rawMap = connection?.hGetAll(key.toByteArray())
                    if (rawMap != null && rawMap.isNotEmpty()) {
                        println("키: $key")
                        println("세션 Hash 데이터:")
                        rawMap.forEach { (field, value) ->
                            val deserializedValue = try {
                                SerializationUtils.deserialize(value)
                            } catch (e: Exception) {
                                String(value)
                            }
                            println("  ${String(field)} : $deserializedValue")
                        }
                        println("--------------------------------------------------")
                    } else {
                        println("키 $key 에 데이터가 없습니다.")
                    }
                }
            } else {
                println("세션 키를 찾을 수 없습니다.")
            }
        }
    }
}

////////////////////////  실행 시 밑의 주석 해제 후 사용  //////////////////////////////

//@Configuration
//class RedisConfig (
//    @Value("\${redis.host}") private val redisHost: String,
//    @Value("\${redis.port}") private val redisPort: Int,
//    @Value("\${redis.password}") private val redisPassword: String
//) {
//    @Bean
//    fun redisConnectionFactory(): RedisConnectionFactory {
//        val redisConfig = RedisStandaloneConfiguration(redisHost, redisPort)
//        if (redisPassword.isNotBlank()) {
//            redisConfig.setPassword(RedisPassword.of(redisPassword))
//        }
//        return LettuceConnectionFactory(redisConfig)
//    }
//
//    @Bean
//    fun stringRedisTemplate(redisConnectionFactory: RedisConnectionFactory): StringRedisTemplate {
//        return StringRedisTemplate(redisConnectionFactory)
//    }
//}

object SerializationUtils {
    /**
     * 주어진 바이트 배열을 역직렬화하여 객체로 복원합니다.
     *
     * @param data 직렬화된 바이트 배열 (null이면 null 반환)
     * @return 역직렬화된 객체
     * @throws RuntimeException 역직렬화 도중 오류 발생 시 예외를 발생시킴
     */
    fun deserialize(data: ByteArray?): Any? {
        if (data == null) return null
        return try {
            ByteArrayInputStream(data).use { bais ->
                ObjectInputStream(bais).use { ois ->
                    ois.readObject()
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("역직렬화 실패", e)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("역직렬화 실패", e)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<RedisSessionParserApplication>(*args)
}