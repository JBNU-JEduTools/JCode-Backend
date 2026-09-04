package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CourseRepository : JpaRepository<Course, Long> {
    fun findByCode(code: String): Course?
    fun courseKey(courseKey: String): MutableList<Course>
    fun findByCodeAndClss(code: String, clss: Int): List<Course>
    fun existsByNamespaceKey(namespaceKey: String): Boolean
}
