package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.dto.course.CourseDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCourseDetailsDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserInfoDto
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseEnvironmentProfile
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceEgressPolicy
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceResourceProfile
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceScope
import org.jbnu.jdevops.jcodeportallogin.entity.CourseInfrastructureAction
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.UserCourses
import org.jbnu.jdevops.jcodeportallogin.entity.MembershipStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentScheduleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.JcodeLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationAction
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationTarget
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.jbnu.jdevops.jcodeportallogin.repo.CourseRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserCoursesRepository
import org.jbnu.jdevops.jcodeportallogin.repo.UserRepository
import org.jbnu.jdevops.jcodeportallogin.repo.StarterArtifactRepository
import org.jbnu.jdevops.jcodeportallogin.repo.JCodeRepository
import org.jbnu.jdevops.jcodeportallogin.util.CourseKeyUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CourseService(
    private val userCoursesRepository: UserCoursesRepository,
    private val assignmentRepository: AssignmentRepository,
    private val courseRepository: CourseRepository,
    private val courseKeyUtil: CourseKeyUtil,
    private val passwordEncoder: PasswordEncoder,
    private val userRepository: UserRepository,
    private val starterArtifactRepository: StarterArtifactRepository,
    private val jCodeRepository: JCodeRepository,
    private val workspaceOperationStore: WorkspaceOperationStore,
    private val infrastructureOperationStore: CourseInfrastructureOperationStore,
    @Value("\${HARBOR_REGISTRY:harbor.jedutools.io}")
    private val harborRegistry: String = "harbor.jedutools.io"
) {
    private data class ResolvedWorkspaceProfile(
        val type: CourseEnvironmentProfile,
        val useVnc: Boolean,
        val useJupyter: Boolean,
        val baseImage: String?,
        val resourceProfile: WorkspaceResourceProfile,
        val egressPolicy: WorkspaceEgressPolicy,
        val workspaceScope: WorkspaceScope
    )

    private fun resolveWorkspaceProfile(dto: CourseDto): ResolvedWorkspaceProfile {
        val type = dto.environmentProfile
            ?: if (dto.vnc == true) CourseEnvironmentProfile.LAB else CourseEnvironmentProfile.ALGORITHM
        val profile = when (type) {
            CourseEnvironmentProfile.ALGORITHM -> ResolvedWorkspaceProfile(
                type, false, false, null, WorkspaceResourceProfile.STANDARD,
                WorkspaceEgressPolicy.PACKAGE_PROXY, WorkspaceScope.COURSE
            )
            CourseEnvironmentProfile.LAB -> ResolvedWorkspaceProfile(
                type, true, true, null, WorkspaceResourceProfile.STANDARD,
                WorkspaceEgressPolicy.PACKAGE_PROXY, WorkspaceScope.COURSE
            )
            CourseEnvironmentProfile.CUSTOM -> ResolvedWorkspaceProfile(
                type = type,
                useVnc = dto.useVnc
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM 환경은 useVnc가 필요합니다."),
                useJupyter = dto.useJupyter
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM 환경은 useJupyter가 필요합니다."),
                baseImage = dto.baseImage?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM 환경은 baseImage가 필요합니다."),
                resourceProfile = dto.resourceProfile
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM 환경은 resourceProfile이 필요합니다."),
                egressPolicy = dto.egressPolicy
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM 환경은 egressPolicy가 필요합니다."),
                workspaceScope = dto.workspaceScope
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM 환경은 workspaceScope가 필요합니다.")
            )
        }
        val registry = harborRegistry.trim().lowercase().removeSuffix("/")
        if (!Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::[1-9][0-9]{0,4})?").matches(registry)) {
            throw IllegalStateException("HARBOR_REGISTRY는 scheme과 경로 없는 registry host여야 합니다.")
        }
        if (profile.baseImage != null && !Regex("^${Regex.escape(registry)}/.+(@sha256:[0-9a-f]{64}|:[^/@]*[0-9a-f]{7,40}(?:[-._][^/]*)?)$").matches(profile.baseImage)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "baseImage는 Harbor commit tag 또는 digest로 고정해야 합니다.")
        }
        return profile
    }

    private fun Course.toDto(courseKeyValue: String? = null): CourseDto = CourseDto(
        courseId = id,
        name = name,
        code = code,
        professor = professor,
        clss = clss,
        year = year,
        term = term,
        vnc = useVnc,
        environmentProfile = environmentProfile,
        useVnc = useVnc,
        useJupyter = useJupyter,
        baseImage = baseImage,
        resourceProfile = resourceProfile,
        egressPolicy = egressPolicy,
        workspaceScope = workspaceScope,
        hwCount = hwCount,
        pracEnabled = pracEnabled,
        pracCount = pracCount,
        status = status,
        endedAt = endedAt?.toString(),
        courseKey = courseKeyValue
    )

    private fun validateCourseManagementAuthority(courseId: Long, email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
        if (user.role == RoleType.ADMIN) {
            return
        }
        val membership = userCoursesRepository.findByUserIdAndCourseId(user.id, courseId)
        if (membership?.role != RoleType.PROFESSOR) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 담당 교수 권한이 없습니다.")
        }
    }
    // 강의별 유저 조회
    @Transactional(readOnly = true)
    fun getUsersByCourse(email: String, courseId: Long): List<UserInfoDto> {
        // 강의가 존재하는지 먼저 확인
        val userCourses = userCoursesRepository.findByCourseId(courseId)

        if (userCourses.isEmpty()) {
            return emptyList()
        }

        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")

        if (currentUser.role != RoleType.ADMIN) {
            val membership = userCoursesRepository.findByUserIdAndCourseId(currentUser.id, courseId)
            if (membership?.role !in setOf(RoleType.PROFESSOR, RoleType.ASSISTANT)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 사용자 조회 권한이 없습니다.")
            }
        }

       return userCourses.map {
           val user = it.user
           val role = it.role
           UserInfoDto(
               userId = user.id,
               name = user.name,
               email = user.email,
               role = user.role,
               courseRole = role,
               studentNum = user.studentNum
           )
       }
    }

    // 강의별 과제 조회
    @Transactional(readOnly = true)
    fun getAssignmentsByCourse(courseId: Long): List<AssignmentDto> {
        val assignments = assignmentRepository.findByCourseId(courseId)

        if (assignments.isEmpty()) return emptyList()

        val starters = starterArtifactRepository.findByAssignmentCourseIdAndStatusOrderByVersionDesc(
            courseId, org.jbnu.jdevops.jcodeportallogin.entity.StarterArtifactStatus.READY
        ).distinctBy { it.assignment.id }.associateBy { it.assignment.id }
        return assignments.map {
            val starter = starters[it.id]
            AssignmentDto(
                assignmentId = it.id,
                assignmentName = it.name,
                assignmentDescription = it.description,
                dirName = it.workspaceKey,
                workspaceKey = it.workspaceKey,
                hasStarterCode = it.hasStarterCode,
                lifecycleStatus = it.lifecycleStatus,
                scheduleStatus = it.scheduleStatus,
                lastError = it.lastError,
                starterVersion = starter?.version,
                starterChecksum = starter?.checksum,
                starterOverwritePolicy = starter?.overwritePolicy,
                archiveRetentionDays = it.archiveRetentionDays,
                archivedAt = it.archivedAt?.toString(),
                finalizedAt = it.finalizedAt?.toString(),
                kickoffDate = it.kickoffDate,
                deadlineDate = it.deadlineDate,
                createdAt = it.createdAt.toString(),
                updatedAt = it.updatedAt.toString()
            )
        }
    }

    // 강의 key 재발급
    @Transactional
    fun reissueCourseKey(courseId: Long, email: String): String {
        validateCourseManagementAuthority(courseId, email)
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        // 새 원본 key 생성
        val newRawKey = courseKeyUtil.generateCourseEnrollmentCode(course.code, course.clss)

        // 암호화하여 업데이트
        val newEncryptedKey = passwordEncoder.encode(newRawKey)
        course.courseKey = newEncryptedKey
        courseRepository.save(course)

        return newRawKey // 새 원본 key(평문)를 반환 (관리자에게 한 번만 노출)
    }

    // 강의 추가 (DB 저장 + Generator에 NS 초기화 요청)
    @Transactional
    fun createCourse(courseDto: CourseDto, creatorEmail: String): CourseDto {
        val profile = resolveWorkspaceProfile(courseDto)
        // 랜덤 key를 생성하여 할당
        val rawKey = courseKeyUtil.generateCourseEnrollmentCode(courseDto.code, courseDto.clss)
        // PasswordEncoder를 사용해 암호화 (해싱) 처리
        val encryptedKey = passwordEncoder.encode(rawKey)

        val course = courseRepository.save(Course(
            name = courseDto.name,
            code = courseDto.code,
            professor = courseDto.professor,
            clss = courseDto.clss,
            year = courseDto.year,
            term = courseDto.term,
            vnc = profile.useVnc,
            environmentProfile = profile.type,
            useVnc = profile.useVnc,
            useJupyter = profile.useJupyter,
            baseImage = profile.baseImage,
            resourceProfile = profile.resourceProfile,
            egressPolicy = profile.egressPolicy,
            workspaceScope = profile.workspaceScope,
            hwCount = courseDto.hwCount,
            pracEnabled = courseDto.pracEnabled,
            pracCount = courseDto.pracCount,
            courseKey = encryptedKey,
            status = CourseStatus.PROVISIONING
        ))

        val creator = userRepository.findByEmail(creatorEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Course creator not found")
        userCoursesRepository.save(
            UserCourses(course = course, user = creator, role = RoleType.PROFESSOR, lifecycleStatus = MembershipStatus.READY)
        )

        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.PROVISION_NAMESPACE)

        return course.toDto(rawKey)
    }

    // 강의 수정
    @Transactional
    fun updateCourse(courseId: Long, courseDto: CourseDto, email: String): CourseDto {
        validateCourseManagementAuthority(courseId, email)
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val requestedProfile = resolveWorkspaceProfile(courseDto)
        if (course.code != courseDto.code || course.clss != courseDto.clss ||
            course.environmentProfile != requestedProfile.type || course.useVnc != requestedProfile.useVnc ||
            course.useJupyter != requestedProfile.useJupyter || course.baseImage != requestedProfile.baseImage ||
            course.resourceProfile != requestedProfile.resourceProfile || course.egressPolicy != requestedProfile.egressPolicy ||
            course.workspaceScope != requestedProfile.workspaceScope
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "강의 코드, 분반, 환경 프로필은 생성 후 변경할 수 없습니다."
            )
        }

        val updatedCourse = course.copy(
            name = courseDto.name,
            year = courseDto.year,
            term = courseDto.term,
            professor = courseDto.professor,
            hwCount = courseDto.hwCount,
            pracEnabled = courseDto.pracEnabled,
            pracCount = courseDto.pracCount
        )
        courseRepository.save(updatedCourse)
        return updatedCourse.toDto()
    }

    @Transactional
    fun deleteCourse(courseId: Long) {
        courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }
        throw ResponseStatusException(HttpStatus.CONFLICT, "강의와 과제 이력은 보관되며 물리 삭제하지 않습니다.")
    }

    // 강의 종료 요청: DB에 desired state와 작업을 기록하고 reconciler가 완료한다.
    @Transactional
    fun endCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ACTIVE 상태의 강의만 종료할 수 있습니다.")
        }

        course.status = CourseStatus.TERMINATING
        courseRepository.save(course)
        assignmentRepository.findByCourseId(course.id).forEach { assignment ->
            when (assignment.lifecycleStatus) {
                AssignmentLifecycleStatus.ACTIVE -> {
                    assignment.scheduleStatus = AssignmentScheduleStatus.CLOSED
                    assignment.lastError = null
                    assignment.updatedAt = LocalDateTime.now()
                    assignmentRepository.save(assignment)
                    if (assignment.finalizedAt == null) {
                        workspaceOperationStore.enqueue(
                            WorkspaceOperationTarget.ASSIGNMENT,
                            assignment.id,
                            WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION
                        )
                    }
                }
                AssignmentLifecycleStatus.PROVISIONING,
                AssignmentLifecycleStatus.PROVISION_FAILED -> {
                    assignment.lifecycleStatus = AssignmentLifecycleStatus.DELETING
                    assignment.scheduleStatus = AssignmentScheduleStatus.CLOSED
                    assignment.lastError = null
                    assignmentRepository.save(assignment)
                    workspaceOperationStore.enqueue(
                        WorkspaceOperationTarget.ASSIGNMENT,
                        assignment.id,
                        WorkspaceOperationAction.ARCHIVE_ASSIGNMENT
                    )
                }
                else -> Unit
            }
        }
        jCodeRepository.findByCourseId(course.id).forEach { jcode ->
            if (jcode.lifecycleStatus !in setOf(
                    JcodeLifecycleStatus.DELETE_PENDING,
                    JcodeLifecycleStatus.ARCHIVED
                )) {
                jcode.lifecycleStatus = JcodeLifecycleStatus.DELETE_PENDING
                jcode.lastError = null
                jCodeRepository.save(jcode)
                workspaceOperationStore.enqueue(
                    WorkspaceOperationTarget.JCODE,
                    jcode.id,
                    WorkspaceOperationAction.DELETE_JCODE
                )
            }
        }
        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.DELETE_WORKLOADS)
    }

    // 강의 아카이브: status → ARCHIVED, NS 삭제
    @Transactional
    fun archiveCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ENDED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ENDED 상태의 강의만 아카이브할 수 있습니다.")
        }

        val incompleteAssignments = assignmentRepository.findByCourseId(course.id).filter { assignment ->
            when (assignment.lifecycleStatus) {
                AssignmentLifecycleStatus.ARCHIVED -> false
                AssignmentLifecycleStatus.ACTIVE ->
                    assignment.scheduleStatus != AssignmentScheduleStatus.CLOSED ||
                        assignment.finalizedAt == null
                else -> true
            }
        }
        if (incompleteAssignments.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "과제 파일 보관이 완료된 뒤 강의를 아카이브할 수 있습니다.")
        }
        if (jCodeRepository.findByCourseId(course.id).any { it.lifecycleStatus != JcodeLifecycleStatus.ARCHIVED }) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "JCode 정리가 완료된 뒤 강의를 아카이브할 수 있습니다.")
        }

        course.status = CourseStatus.ARCHIVING
        courseRepository.save(course)
        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.DELETE_NAMESPACE)
    }

    // 강의 재개설: status → ACTIVE, NS 재생성
    @Transactional
    fun reopenCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (course.status != CourseStatus.ENDED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ENDED 상태의 강의만 재개설할 수 있습니다.")
        }

        course.status = CourseStatus.PROVISIONING
        courseRepository.save(course)
        infrastructureOperationStore.enqueue(course.id, CourseInfrastructureAction.PROVISION_NAMESPACE)
    }

    fun retryInfrastructure(courseId: Long) {
        if (!infrastructureOperationStore.retryFailed(courseId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "재시도할 실패 작업이 없습니다.")
        }
    }

    // 전체 강의 조회
    @Transactional(readOnly = true)
    fun getAllCourses(): List<CourseDto> {
        return courseRepository.findAll()
            .map { it.toDto() }
    }

    // 관리자용 강의 상세 정보 조회
    @Transactional(readOnly = true)
    fun getCourseDetails(courseId: Long): UserCourseDetailsDto {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val starters = starterArtifactRepository.findByAssignmentCourseIdAndStatusOrderByVersionDesc(
            courseId, org.jbnu.jdevops.jcodeportallogin.entity.StarterArtifactStatus.READY
        ).distinctBy { it.assignment.id }.associateBy { it.assignment.id }
        val assignments = assignmentRepository.findByCourseId(courseId)
            .map { assignment ->
                val starter = starters[assignment.id]
                AssignmentDto(
                    assignmentId = assignment.id,
                    assignmentName = assignment.name,
                    assignmentDescription = assignment.description,
                    dirName = assignment.workspaceKey,
                    workspaceKey = assignment.workspaceKey,
                    hasStarterCode = assignment.hasStarterCode,
                    lifecycleStatus = assignment.lifecycleStatus,
                    scheduleStatus = assignment.scheduleStatus,
                    lastError = assignment.lastError,
                    starterVersion = starter?.version,
                    starterChecksum = starter?.checksum,
                    starterOverwritePolicy = starter?.overwritePolicy,
                    archiveRetentionDays = assignment.archiveRetentionDays,
                    archivedAt = assignment.archivedAt?.toString(),
                    finalizedAt = assignment.finalizedAt?.toString(),
                    kickoffDate = assignment.kickoffDate,
                    deadlineDate = assignment.deadlineDate,
                    createdAt = assignment.createdAt.toString(),
                    updatedAt = assignment.updatedAt.toString()
                )
            }

        return UserCourseDetailsDto(
            courseId = course.id,
            courseName = course.name,
            courseCode = course.code,
            courseProfessor = course.professor,
            courseYear = course.year,
            courseTerm = course.term,
            courseClss = course.clss,
            hwCount = course.hwCount,
            pracEnabled = course.pracEnabled,
            pracCount = course.pracCount,
            status = course.status,
            environmentProfile = course.environmentProfile,
            workspaceScope = course.workspaceScope,
            assignments = assignments,
            jcodeUrl = null // 관리자는 JCode URL이 필요 없음
        )
    }
}
