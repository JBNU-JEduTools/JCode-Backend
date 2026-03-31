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
        when (currentUserRole) {
            RoleType.STUDENT -> {
                // 수업별 조교 역할 확인
                val userCourses = userCoursesRepository.findByUserIdAndCourseId(currentUserId, courseId)
                if (userCourses?.role == RoleType.ASSISTANT) {
                    // 조교는 다른 학생 접근 허용
                    return
                }
                if (currentUserId != targetUserId) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 권한이 없습니다.")
                }
            }
            RoleType.PROFESSOR -> {
                if (!userCoursesRepository.existsByUserIdAndCourseId(currentUserId, courseId)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의에 권한이 없습니다.")
                }
            }
            else -> {}  // ADMIN은 모든 권한을 가짐
        }
    }
}
