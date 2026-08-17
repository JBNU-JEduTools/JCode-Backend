package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType

interface UserCoursesRepository : JpaRepository<UserCourses, Long> {
    fun findByUserId(userId: Long): List<UserCourses>
//    @Query("select uc, u from UserCourses uc left join User u on uc.user = u where uc.course.id = :courseId")
    fun findByCourseId(courseId: Long): List<UserCourses>
    fun findByUserIdAndCourseId(userId: Long, courseId: Long): UserCourses?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from UserCourses membership where membership.user.id = :userId and membership.course.id = :courseId")
    fun findByUserIdAndCourseIdForUpdate(userId: Long, courseId: Long): UserCourses?
    fun existsByUserIdAndCourseId(userId: Long, courseId: Long): Boolean
    fun findByUserIdAndCourseCode(userId: Long, courseCode: String): UserCourses?
    fun findByUserEmailAndRole(email: String, role: RoleType): List<UserCourses>
    fun countUserCoursesByCourseIdAndRole(courseId: Long, role: RoleType): Int
    fun existsByCourseIdAndUserIdAndRole(courseId: Long, userId: Long, role: RoleType): Boolean
    fun findByUserStudentNumAndCourseId(studentNum: Int, courseId: Long): UserCourses?
    fun findByCourseIdAndRole(courseId: Long, role: RoleType): List<UserCourses>
}
