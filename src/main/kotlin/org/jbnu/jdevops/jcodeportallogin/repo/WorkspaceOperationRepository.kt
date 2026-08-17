package org.jbnu.jdevops.jcodeportallogin.repo

import jakarta.persistence.LockModeType
import org.jbnu.jdevops.jcodeportallogin.entity.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface WorkspaceOperationRepository : JpaRepository<WorkspaceOperation, Long> {
    fun existsByTargetTypeAndTargetIdAndStatusIn(
        targetType: WorkspaceOperationTarget,
        targetId: Long,
        statuses: Collection<WorkspaceOperationStatus>
    ): Boolean

    fun findTopByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
        targetType: WorkspaceOperationTarget,
        targetId: Long,
        status: WorkspaceOperationStatus
    ): WorkspaceOperation?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select operation from WorkspaceOperation operation
        where ((operation.status = :pending and operation.nextAttemptAt <= :now)
           or (operation.status = :processing and operation.lockedAt < :staleBefore))
          and not exists (
              select older.id from WorkspaceOperation older
              where older.targetType = operation.targetType
                and older.targetId = operation.targetId
                and older.status in (:pending, :processing)
                and (older.createdAt < operation.createdAt
                  or (older.createdAt = operation.createdAt and older.id < operation.id))
          )
        order by operation.createdAt asc
        """
    )
    fun findReadyForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("staleBefore") staleBefore: LocalDateTime,
        @Param("pending") pending: WorkspaceOperationStatus,
        @Param("processing") processing: WorkspaceOperationStatus,
        pageable: Pageable
    ): List<WorkspaceOperation>
}
