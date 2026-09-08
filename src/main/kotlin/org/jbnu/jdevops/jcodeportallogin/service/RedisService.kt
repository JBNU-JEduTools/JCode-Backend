package org.jbnu.jdevops.jcodeportallogin.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.security.MessageDigest

@Service
class RedisService(
    private val redisTemplate: StringRedisTemplate
) {
    // 프로필 조회하는 메서드 (접근 시마다 TTL 다시 세팅)
    fun getUserProfile(uuid: String): MutableMap<String, String>? {
        val key = "user:profile:$uuid"
        val ops = redisTemplate.opsForHash<String, String>()

        // 해시 전체 필드 조회
        val result = ops.entries(key)

        // result가 비어있지 않다면 => 키가 존재하므로 TTL 갱신
        if (result.isNotEmpty()) {
            redisTemplate.expire(key, 6, TimeUnit.HOURS)
        }

        return if (result.isEmpty()) null else result
    }

    // JCode URL을 Redis에서 가져오기
    fun getJcodeUrl(infrastructureKey: String): String? {
        return redisTemplate.opsForValue().get("course:$infrastructureKey:jcode-url")
    }

    // 이메일 & 강의코드 & 분반 → JCode URL 저장
    fun storeUserCourse(email: String, infrastructureKey: String, courseClss: Int, jcodeUrl: String) {
        val key = "user:$email:course:$infrastructureKey:$courseClss"
        redisTemplate.opsForValue().set(key, jcodeUrl, 180, TimeUnit.DAYS) // 유효기간 6개월 (약 180일)
    }

    // 이메일 & 강의코드 & 분반 → JCode URL 저장
    fun storeSnapshotUserCourse(email: String, infrastructureKey: String, courseClss: Int, jcodeUrl: String) {
        val key = "user:$email:course:$infrastructureKey:$courseClss:snapshot"
        redisTemplate.opsForValue().set(key, jcodeUrl, 180, TimeUnit.DAYS) // 유효기간 6개월 (약 180일)
    }

    fun storeJcodeRoute(jcodeId: Long, jcodeUrl: String) {
        redisTemplate.opsForValue().set("jcode:$jcodeId:route", jcodeUrl, 180, TimeUnit.DAYS)
    }

    fun deleteJcodeRoute(jcodeId: Long) {
        redisTemplate.delete("jcode:$jcodeId:route")
    }

    // 이메일 & 강의코드 & 분반 → 일반·스냅샷 JCode URL을 함께 삭제
    fun deleteUserCourseAccess(email: String, infrastructureKey: String, courseClss: Int) {
        val baseKey = "user:$email:course:$infrastructureKey:$courseClss"
        redisTemplate.delete(baseKey)
        redisTemplate.delete("$baseKey:snapshot")
    }

    // 강의 관리자 목록에 특정 사용자가 있는지 확인
    fun isUserInCourseManagers(infrastructureKey: String, courseClss: Int, email: String): Boolean {
        val key = "course:$infrastructureKey:$courseClss:managers"
        return redisTemplate.opsForSet().isMember(key, email) ?: false
    }

    // 강의 관리자 목록에 유저 추가
    fun addUserToCourseManagerList(infrastructureKey: String, courseClss: Int, email: String) {
        val key = "course:$infrastructureKey:$courseClss:managers"
        redisTemplate.opsForSet().add(key, email)
    }

    // 강의 관리자 목록에서 특정 유저 제거
    fun removeUserFromCourseManagerList(infrastructureKey: String, courseClss: Int, email: String) {
        val key = "course:$infrastructureKey:$courseClss:managers"
        redisTemplate.opsForSet().remove(key, email)
    }

    fun replaceCourseManagers(infrastructureKey: String, courseClss: Int, emails: Collection<String>) {
        val key = "course:$infrastructureKey:$courseClss:managers"
        val members = emails.filter(String::isNotBlank).distinct().sorted()
        redisTemplate.execute(REPLACE_SET_SCRIPT, listOf(key), *members.toTypedArray())
    }

    // id_token을 하나의 Redis 해시로 저장 (key: "user:id_tokens", field: 이메일, value: id_token)
    fun storeIdToken(email: String, idToken: String) {
        val hashKey = "user:id_tokens"
        redisTemplate.opsForHash<String, String>().put(hashKey, email, idToken)
        // 해시 전체에 TTL을 설정하고 싶다면, 해시 key에 TTL을 부여할 수 있습니다.
        // redisTemplate.expire(hashKey, 1, TimeUnit.HOURS)
    }

    fun getIdToken(email: String): String? {
        val hashKey = "user:id_tokens"
        return redisTemplate.opsForHash<String, String>().get(hashKey, email)
    }

    fun deleteIdToken(email: String) {
        val hashKey = "user:id_tokens"
        redisTemplate.opsForHash<String, String>().delete(hashKey, email)
    }

    // refresh token을 하나의 Redis 해시로 관리 (key: "user:refresh_tokens", field: 이메일, value: refresh token)
    fun storeRefreshToken(email: String, refreshToken: String) {
        val hashKey = "user:refresh_tokens"
        redisTemplate.opsForHash<String, String>().put(hashKey, email, refreshToken)
    }

    fun getRefreshToken(email: String): String? {
        val hashKey = "user:refresh_tokens"
        return redisTemplate.opsForHash<String, String>().get(hashKey, email)
    }

    fun deleteRefreshToken(email: String) {
        val hashKey = "user:refresh_tokens"
        redisTemplate.opsForHash<String, String>().delete(hashKey, email)
    }

    fun rotateRefreshToken(email: String, presentedToken: String, candidateToken: String, graceSeconds: Long = 10): String? {
        val graceKey = "refresh:replay:${tokenDigest(presentedToken)}"
        val result = redisTemplate.execute(
            ROTATE_REFRESH_SCRIPT,
            listOf("user:refresh_tokens", graceKey),
            email,
            presentedToken,
            candidateToken,
            graceSeconds.toString()
        )
        return result?.takeIf(String::isNotBlank)
    }

    fun deleteRefreshTokenIfMatches(email: String, refreshToken: String): Boolean =
        redisTemplate.execute(
            DELETE_REFRESH_IF_MATCHES_SCRIPT,
            listOf("user:refresh_tokens"),
            email,
            refreshToken
        ) == 1L

    // JWT 원문이 Redis key나 운영 도구 출력에 노출되지 않도록 digest만 저장한다.
    // 인자로 받은 만료 시간(expireTimeMillis)을 사용하여 TTL을 JWT 만료 시간과 같게 설정
    fun addToJwtBlacklist(jwt: String, expireTimeMillis: Long) {
        val key = "blacklist:jwt:${tokenDigest(jwt)}"
        redisTemplate.opsForValue().set(key, "true", expireTimeMillis, TimeUnit.MILLISECONDS)
    }

    fun isJwtBlacklisted(jwt: String): Boolean {
        val key = "blacklist:jwt:${tokenDigest(jwt)}"
        // 기존 raw-token key는 TTL이 소진될 때까지만 읽어 rolling migration을 보장한다.
        return redisTemplate.hasKey(key) || redisTemplate.hasKey("blacklist:jwt:$jwt")
    }

    // 사용자 정보를 하나의 해시로 저장 (키: "user:profile:<UUID>")
    fun storeUserProfile(
        email: String,
        studentNumber: String,
        infrastructureKey: String,
        clss: String,
        snapshot: String,
        jcodeId: Long
    ): String {
        // UUID 생성
        val id = UUID.randomUUID().toString()
        // 키 예시: user:profile:123e4567-e89b-12d3-a456-426614174000
        val key = "user:profile:$id"
        // 저장할 필드와 값을 Map으로 구성
        val userInfo = mapOf(
            "email" to email,
            "studentNumber" to studentNumber,
            // The field name is retained for compatibility with the current router contract.
            "courseCode" to infrastructureKey,
            "clss" to clss,
            "snapshot" to snapshot,
            "jcodeId" to jcodeId.toString(),
            "routeVersion" to "2"
        )
        // Redis 해시에 여러 필드를 한 번에 저장
        redisTemplate.opsForHash<String, String>().putAll(key, userInfo)

        // TTL 6시간 설정
        redisTemplate.expire(key, 6, TimeUnit.HOURS)

        return id
    }

    companion object {
        private fun tokenDigest(token: String): String = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        private val ROTATE_REFRESH_SCRIPT = DefaultRedisScript(
            """
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if current == ARGV[2] then
                redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
                redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
                return ARGV[3]
            end
            local replay = redis.call('GET', KEYS[2])
            if replay and current == replay then
                return current
            end
            return ''
            """.trimIndent(),
            String::class.java
        )

        private val DELETE_REFRESH_IF_MATCHES_SCRIPT = DefaultRedisScript(
            """
            if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then
                return redis.call('HDEL', KEYS[1], ARGV[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java
        )

        private val REPLACE_SET_SCRIPT = DefaultRedisScript(
            """
            redis.call('DEL', KEYS[1])
            if #ARGV == 0 then
                return 0
            end
            return redis.call('SADD', KEYS[1], unpack(ARGV))
            """.trimIndent(),
            Long::class.java
        )
    }
}
