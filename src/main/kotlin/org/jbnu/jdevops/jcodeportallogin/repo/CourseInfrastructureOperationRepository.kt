package org.jbnu.jdevops.jcodeportallogin.repo

import jakarta.persistence.LockModeType
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureOperation
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureOperationStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CourseInfrastructureOperationRepository : JpaRepository<CourseInfrastructureOperation, Long> {
    fun existsByCourseIdAndStatusIn(
        courseId: Long,
        statuses: Collection<CourseInfrastructureOperationStatus>
    ): Boolean

    fun findTopByCourseIdAndStatusOrderByCreatedAtDesc(
        courseId: Long,
        status: CourseInfrastructureOperationStatus
    ): CourseInfrastructureOperation?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select operation from CourseInfrastructureOperation operation
        where (operation.status = :pending
               and operation.nextAttemptAt <= :now)
           or (operation.status = :processing
               and operation.lockedAt <= :staleBefore)
        order by operation.createdAt asc
        """
    )
    fun findReadyForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("staleBefore") staleBefore: LocalDateTime,
        @Param("pending") pending: CourseInfrastructureOperationStatus,
        @Param("processing") processing: CourseInfrastructureOperationStatus,
        pageable: Pageable
    ): List<CourseInfrastructureOperation>
}
