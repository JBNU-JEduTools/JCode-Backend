package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentPathBackfillStatus
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.util.Optional

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    fun findByCourseId(courseId: Long): List<Assignment>
    fun findByIdAndCourseId(id: Long, courseId: Long): Optional<Assignment>
    fun existsByCourseIdAndName(courseId: Long, assignmentName: String): Boolean
    fun findByPathBackfillStatus(status: AssignmentPathBackfillStatus): List<Assignment>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select assignment from Assignment assignment
        where assignment.course.status = :courseStatus
          and assignment.lifecycleStatus = :assignmentStatus
        """
    )
    fun findSchedulableForUpdate(
        @Param("courseStatus") courseStatus: CourseStatus,
        @Param("assignmentStatus") assignmentStatus: AssignmentLifecycleStatus
    ): List<Assignment>
}
