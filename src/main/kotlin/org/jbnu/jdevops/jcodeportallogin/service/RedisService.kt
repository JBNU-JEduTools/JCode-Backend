package org.jbnu.jdevops.jcodeportallogin.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.UUID

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
    fun getJcodeUrl(courseCode: String): String? {
        return redisTemplate.opsForValue().get("course:$courseCode:jcode-url")
    }

    // 이메일 & 강의코드 & 분반 → JCode URL 저장
    fun storeUserCourse(email: String, courseCode: String, courseClss: Int, jcodeUrl: String) {
        val key = "user:$email:course:$courseCode:$courseClss"
        redisTemplate.opsForValue().set(key, jcodeUrl, 180, TimeUnit.DAYS) // 유효기간 6개월 (약 180일)
    }

    // 이메일 & 강의코드 & 분반 → JCode URL 저장
    fun storeSnapshotUserCourse(email: String, courseCode: String, courseClss: Int, jcodeUrl: String) {
        val key = "user:$email:course:$courseCode:$courseClss:snapshot"
        redisTemplate.opsForValue().set(key, jcodeUrl, 180, TimeUnit.DAYS) // 유효기간 6개월 (약 180일)
    }

    // 이메일 & 강의코드 & 분반 → 일반·스냅샷 JCode URL을 함께 삭제
    fun deleteUserCourseAccess(email: String, courseCode: String, courseClss: Int) {
        val baseKey = "user:$email:course:$courseCode:$courseClss"
        redisTemplate.delete(baseKey)
        redisTemplate.delete("$baseKey:snapshot")
    }

    // 강의 관리자 목록에 특정 사용자가 있는지 확인
    fun isUserInCourseManagers(courseCode: String, courseClss: Int, email: String): Boolean {
        val key = "course:$courseCode:$courseClss:managers"
        return redisTemplate.opsForSet().isMember(key, email) ?: false
    }

    // 강의 관리자 목록에 유저 추가
    fun addUserToCourseManagerList(courseCode: String, courseClss: Int, email: String) {
        val key = "course:$courseCode:$courseClss:managers"
        redisTemplate.opsForSet().add(key, email)
    }

    // 강의 관리자 목록에서 특정 유저 제거
    fun removeUserFromCourseManagerList(courseCode: String, courseClss: Int, email: String) {
        val key = "course:$courseCode:$courseClss:managers"
        redisTemplate.opsForSet().remove(key, email)
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

    // JWT 블랙리스트에 저장 (key: "blacklist:jwt:<token>")
    // 인자로 받은 만료 시간(expireTimeMillis)을 사용하여 TTL을 JWT 만료 시간과 같게 설정
    fun addToJwtBlacklist(jwt: String, expireTimeMillis: Long) {
        val key = "blacklist:jwt:$jwt"
        redisTemplate.opsForValue().set(key, "true", expireTimeMillis, TimeUnit.MILLISECONDS)
    }

    fun isJwtBlacklisted(jwt: String): Boolean {
        val key = "blacklist:jwt:$jwt"
        return redisTemplate.hasKey(key) ?: false
    }

    // 사용자 정보를 하나의 해시로 저장 (키: "user:profile:<UUID>")
    fun storeUserProfile(email: String, studentNumber: String, courseCode: String, clss: String, snapshot: String): String {
        // UUID 생성
        val id = UUID.randomUUID().toString()
        // 키 예시: user:profile:123e4567-e89b-12d3-a456-426614174000
        val key = "user:profile:$id"
        // 저장할 필드와 값을 Map으로 구성
        val userInfo = mapOf(
            "email" to email,
            "studentNumber" to studentNumber,
            "courseCode" to courseCode,
            "clss" to clss,
            "snapshot" to snapshot
        )
        // Redis 해시에 여러 필드를 한 번에 저장
        redisTemplate.opsForHash<String, String>().putAll(key, userInfo)

        // TTL 6시간 설정
        redisTemplate.expire(key, 6, TimeUnit.HOURS)

        return id
    }
}
