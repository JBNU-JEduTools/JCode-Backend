package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.Jcode
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.entity.JcodeLifecycleStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.util.Optional

interface JCodeRepository : JpaRepository<Jcode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Jcode j where j.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<Jcode>

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
