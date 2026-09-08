package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.MembershipStatus
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class CourseManagerCacheReconciler(
    private val courseRepository: CourseRepository,
    private val userCoursesRepository: UserCoursesRepository,
    private val redisService: RedisService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${redis.course-manager-reconcile-initial-delay-ms:5000}",
        fixedDelayString = "\${redis.course-manager-reconcile-ms:30000}",
        scheduler = "courseManagerCacheScheduler"
    )
    fun reconcile() {
        try {
            val managersByCourse = userCoursesRepository.findActiveCourseManagers(
                setOf(RoleType.PROFESSOR, RoleType.ASSISTANT),
                MembershipStatus.READY,
                CourseStatus.ACTIVE
            ).groupBy { it.course.infrastructureKey to it.course.clss }
                .mapValues { (_, memberships) -> memberships.map { it.user.email }.toSet() }

            courseRepository.findAll()
                .map { it.infrastructureKey to it.clss }
                .toSet()
                .forEach { (infrastructureKey, courseClss) ->
                    redisService.replaceCourseManagers(
                        infrastructureKey,
                        courseClss,
                        managersByCourse[infrastructureKey to courseClss].orEmpty()
                    )
                }
        } catch (exception: Exception) {
            logger.warn("수업별 관리자 Redis 캐시 동기화에 실패했습니다. 다음 주기에 재시도합니다.", exception)
        }
    }
}
