package org.jbnu.jdevops.jcodeportallogin.util

import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

object AuthorizationUtil {
    fun validateUserAuthority(
        currentUserRole: RoleType,
        currentUserId: Long,
        targetUserId: Long,
        courseId: Long,
        userCoursesRepository: UserCoursesRepository
    ) {
        if (currentUserRole == RoleType.ADMIN) return

        val membership = userCoursesRepository.findByUserIdAndCourseId(currentUserId, courseId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의에 소속되어 있지 않습니다.")

        if (currentUserId == targetUserId) return
        if (membership.role !in setOf(RoleType.PROFESSOR, RoleType.ASSISTANT)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 다른 사용자 조회 권한이 없습니다.")
        }
    }
}
