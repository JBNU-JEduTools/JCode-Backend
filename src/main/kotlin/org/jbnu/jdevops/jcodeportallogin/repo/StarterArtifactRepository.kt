package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.StarterArtifact
import org.jbnu.jdevops.jcodeportallogin.entity.StarterArtifactStatus
import org.springframework.data.jpa.repository.JpaRepository

interface StarterArtifactRepository : JpaRepository<StarterArtifact, Long> {
    fun findTopByAssignmentIdOrderByVersionDesc(assignmentId: Long): StarterArtifact?
    fun findTopByAssignmentIdAndStatusOrderByVersionDesc(assignmentId: Long, status: StarterArtifactStatus): StarterArtifact?
    fun findByAssignmentCourseIdAndStatusOrderByVersionDesc(courseId: Long, status: StarterArtifactStatus): List<StarterArtifact>
}
