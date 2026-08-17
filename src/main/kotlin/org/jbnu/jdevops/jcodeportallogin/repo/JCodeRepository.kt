package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.Jcode
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.entity.JcodeLifecycleStatus
import org.springframework.data.jpa.repository.JpaRepository

interface JCodeRepository : JpaRepository<Jcode, Long> {
    fun findByUserId(userId: Long): List<Jcode>
    fun findByUserCourse(userCourse: UserCourses): Jcode?
    fun findAllByUserCourse(userCourse: UserCourses): List<Jcode>
    fun findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIdAndLifecycleStatusNotOrderByIdDesc(
        userId: Long, courseId: Long, snapshot: Boolean, assignmentId: Long, excludedStatus: JcodeLifecycleStatus
    ): Jcode?
    fun findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIsNullAndLifecycleStatusNotOrderByIdDesc(
        userId: Long, courseId: Long, snapshot: Boolean, excludedStatus: JcodeLifecycleStatus
    ): Jcode?
    fun findByCourseId(courseId: Long): List<Jcode>
    fun findByAssignmentId(assignmentId: Long): List<Jcode>
}
