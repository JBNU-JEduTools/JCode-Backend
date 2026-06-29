package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.springframework.data.jpa.repository.JpaRepository

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    fun findByCourseId(courseId: Long): List<Assignment>
    fun existsByCourseIdAndName(courseId: Long, assignmentName: String): Boolean
    fun existsByCourseIdAndDirName(courseId: Long, dirName: String): Boolean
}