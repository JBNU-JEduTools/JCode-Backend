package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.jcode.JCodeDto
import org.jbnu.jdevops.jcodeportallogin.entity.*
import org.jbnu.jdevops.jcodeportallogin.repo.*
import org.jbnu.jdevops.jcodeportallogin.util.AuthorizationUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class JCodeService(
    private val jCodeRepository: JCodeRepository,
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val userCoursesRepository: UserCoursesRepository,
    private val assignmentRepository: AssignmentRepository,
    private val workspaceOperationStore: WorkspaceOperationStore
) {
    @Transactional
    fun createJCode(
        courseId: Long,
        userEmail: String,
        email: String,
        token: String,
        snapshot: Boolean,
        assignmentId: Long? = null
    ): JCodeDto {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }
        if (course.status != CourseStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "종료된 강의에서는 JCode를 생성할 수 없습니다.")
        }
        val actor = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val target = userRepository.findByEmail(userEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TargetUser not found")
        val membership = userCoursesRepository.findByUserIdAndCourseIdForUpdate(target.id, course.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TargetUserCourse not found")
        if (membership.lifecycleStatus != MembershipStatus.READY) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "학생 Workspace 준비가 완료되지 않았습니다.")
        }
        AuthorizationUtil.validateUserAuthority(actor.role, actor.id, target.id, course.id, userCoursesRepository)
        if (snapshot) AuthorizationUtil.validateUserAuthority(actor.role, actor.id, 0, course.id, userCoursesRepository)

        val assignment = if (course.workspaceScope == WorkspaceScope.ASSIGNMENT && !snapshot) {
            val id = assignmentId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 단위 환경은 assignmentId가 필요합니다.")
            assignmentRepository.findByIdAndCourseId(id, course.id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found in course") }
                .also {
                    if (it.lifecycleStatus != AssignmentLifecycleStatus.ACTIVE || it.scheduleStatus != AssignmentScheduleStatus.OPEN) {
                        throw ResponseStatusException(HttpStatus.CONFLICT, "현재 열려 있는 과제만 IDE에 진입할 수 있습니다.")
                    }
                }
        } else null

        val existing = if (assignment == null) {
            jCodeRepository.findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIsNullAndLifecycleStatusNotOrderByIdDesc(
                target.id, course.id, snapshot, JcodeLifecycleStatus.ARCHIVED
            )
        } else {
            jCodeRepository.findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIdAndLifecycleStatusNotOrderByIdDesc(
                target.id, course.id, snapshot, assignment.id, JcodeLifecycleStatus.ARCHIVED
            )
        }
        if (existing != null && existing.lifecycleStatus != JcodeLifecycleStatus.ARCHIVED) {
            return existing.toDto()
        }

        val suffix = assignment?.let { "-${it.id}" } ?: ""
        val deploymentName = if (snapshot) {
            "jcode-snapshot-${course.code.lowercase()}-${target.studentNum}$suffix"
        } else {
            "jcode-${course.code.lowercase()}-${course.clss}-${target.studentNum}$suffix"
        }
        val jcode = jCodeRepository.saveAndFlush(
            Jcode(
                userCourse = membership,
                course = course,
                user = target,
                assignment = assignment,
                instanceKey = "${membership.id}:${assignment?.id ?: 0}:$snapshot",
                snapshot = snapshot,
                deploymentName = deploymentName,
                serviceName = "$deploymentName-svc",
                lifecycleStatus = JcodeLifecycleStatus.PROVISIONING
            )
        )
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.JCODE, jcode.id, WorkspaceOperationAction.PROVISION_JCODE
        )
        return jcode.toDto()
    }

    @Transactional
    fun deleteJCode(userEmail: String, courseId: Long, token: String, snapshot: Boolean, assignmentId: Long? = null) {
        val user = userRepository.findByEmail(userEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val jcode = if (assignmentId == null) {
            jCodeRepository.findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIsNullAndLifecycleStatusNotOrderByIdDesc(
                user.id, courseId, snapshot, JcodeLifecycleStatus.ARCHIVED
            )
        } else {
            jCodeRepository.findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIdAndLifecycleStatusNotOrderByIdDesc(
                user.id, courseId, snapshot, assignmentId, JcodeLifecycleStatus.ARCHIVED
            )
        }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "JCode not found for the specified user and course")
        enqueueDelete(jcode)
    }

    @Transactional
    fun deleteAllJCodesForUserCourse(userCourse: UserCourses, token: String) {
        jCodeRepository.findAllByUserCourse(userCourse).forEach(::enqueueDelete)
    }

    private fun enqueueDelete(jcode: Jcode) {
        if (jcode.lifecycleStatus == JcodeLifecycleStatus.ARCHIVED) return
        jcode.lifecycleStatus = JcodeLifecycleStatus.DELETE_PENDING
        jcode.lastError = null
        jCodeRepository.save(jcode)
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.JCODE, jcode.id, WorkspaceOperationAction.DELETE_JCODE
        )
    }

    @Transactional
    fun retryJCode(actorEmail: String, userEmail: String, courseId: Long, snapshot: Boolean, assignmentId: Long? = null) {
        val actor = userRepository.findByEmail(actorEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
        val user = userRepository.findByEmail(userEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        AuthorizationUtil.validateUserAuthority(actor.role, actor.id, user.id, courseId, userCoursesRepository)
        val jcode = if (assignmentId == null) {
            jCodeRepository.findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIsNullAndLifecycleStatusNotOrderByIdDesc(
                user.id, courseId, snapshot, JcodeLifecycleStatus.ARCHIVED
            )
        } else {
            jCodeRepository.findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIdAndLifecycleStatusNotOrderByIdDesc(
                user.id, courseId, snapshot, assignmentId, JcodeLifecycleStatus.ARCHIVED
            )
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "JCode not found")
        if (jcode.lifecycleStatus !in setOf(JcodeLifecycleStatus.PROVISION_FAILED, JcodeLifecycleStatus.DELETE_FAILED)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "현재 JCode에 실패한 작업이 없습니다.")
        }
        if (!workspaceOperationStore.retry(WorkspaceOperationTarget.JCODE, jcode.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "재시도할 실패 작업이 없습니다.")
        }
    }

    private fun Jcode.toDto() = JCodeDto(
        jcodeId = id,
        courseName = course.name,
        status = lifecycleStatus,
        jcodeUrl = jcodeUrl,
        assignmentId = assignment?.id,
        lastError = lastError?.let {
            "JCode 환경 처리 중 오류가 발생했습니다. 잠시 후 재시도하거나 관리자에게 문의해주세요."
        }
    )
}
