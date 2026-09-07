package org.jbnu.jdevops.jcodeportallogin.repo

import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.util.Optional

@Repository
interface CourseRepository : JpaRepository<Course, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<Course>

    fun courseKey(courseKey: String): MutableList<Course>
    fun findByInfrastructureKeyAndClss(infrastructureKey: String, clss: Int): List<Course>
    fun existsByNamespaceKey(namespaceKey: String): Boolean
}
