package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    fun findByCourseId(courseId: Long): List<Assignment>
    fun findByIdAndCourseId(id: Long, courseId: Long): Optional<Assignment>
    fun existsByCourseIdAndName(courseId: Long, assignmentName: String): Boolean
}
