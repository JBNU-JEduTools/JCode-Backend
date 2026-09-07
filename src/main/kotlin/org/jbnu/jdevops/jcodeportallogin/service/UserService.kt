package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.dto.assignment.AssignmentDto
import org.jbnu.jdevops.jcodeportallogin.dto.auth.RegisterUserDto
import org.jbnu.jdevops.jcodeportallogin.dto.jcode.JCodeDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserInfoDto
import org.jbnu.jdevops.jcodeportallogin.dto.user.UserProfileUpdateDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCourseDetailsDto
import org.jbnu.jdevops.jcodeportallogin.dto.usercourse.UserCoursesDto
import org.jbnu.jdevops.jcodeportallogin.entity.*
import org.jbnu.jdevops.jcodeportallogin.repo.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val userRepository: UserRepository,
    private val loginRepository: LoginRepository,
    private val jcodeRepository: JCodeRepository,
    private val userCoursesRepository: UserCoursesRepository,
    private val assignmentRepository: AssignmentRepository,
    private val passwordEncoder: PasswordEncoder,
    private val courseRepository: CourseRepository,
    private val redisService: RedisService,
    private val jCodeService: JCodeService,
    private val workspaceOperationStore: WorkspaceOperationStore
) {
    @Transactional
    fun register(registerUserDto: RegisterUserDto): ResponseEntity<String> {
        // 이메일 중복 확인
        if (userRepository.findByEmail(registerUserDto.email) != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use")
        }

        // 비밀번호 유효성 검사
        if (registerUserDto.password.length < 8) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters long")
        }

        return try {
            // 비밀번호 해싱
            val hashedPassword = passwordEncoder.encode(registerUserDto.password)

            // 새 사용자 저장 (전역 ASSISTANT 역할은 허용하지 않음 — 수업별로만 관리)
            val safeRole = if (registerUserDto.role == RoleType.ASSISTANT) RoleType.STUDENT else registerUserDto.role
            val user = userRepository.save(
                User(
                    email = registerUserDto.email,
                    role = safeRole,
                    studentNum = registerUserDto.studentNum
                )
            )

            // 로그인 정보 저장
            loginRepository.save(Login(user = user, password = hashedPassword))

            ResponseEntity.ok("Signup successful")
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to register user")
        }
    }

    @Transactional(readOnly = true)
    fun getUserInfo(email: String): UserInfoDto {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("User not found with email: $email")

        return UserInfoDto(
            userId = user.id,
            email = user.email,
            name = user.name,
            role = user.role,
            studentNum = user.studentNum
        )
    }

    // 내 정보 수정: 이름은 수정 가능하며, 학생번호는 아직 설정되지 않은 경우에만 수정할 수 있음
    @Transactional
    fun updateUserInfo(email: String, updateDto: UserProfileUpdateDto): Map<String, String> {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        // 이름 수정: updateDto.name이 null이 아니고, 기존 이름과 다를 경우에 user.name에 값을 할당
        if (updateDto.name != user.name) {
            user.name = updateDto.name
        }

        // 학생번호 수정: updateDto.studentNum이 null이 아니라면 updateStudentNum() 메서드 호출
        var message: String = ""
        try {
            user.updateStudentNum(updateDto.studentNum)
            message = "User information updated successfully"
        } catch (e: IllegalStateException) {
            // 이미 학생번호가 설정되어 있는 경우 로깅
            message = "User information updated, but student number update was ignored (already set)"
        }

        // 변경된 내용 저장
        val updatedUser = userRepository.save(user)

        return mapOf("message" to message)
    }

    // 유저별 강의 정보 조회
    @Transactional(readOnly = true)
    fun getUserCourses(email: String): List<UserCoursesDto> {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("User not found with email: $email")

        val userCourses = user.courses.filter(::isVisibleMembership)
        return userCourses.map {
            UserCoursesDto(
                courseId = it.course.id,
                courseName = it.course.name,
                courseProfessor = it.course.professor,
                courseClss = it.course.clss,
                courseTerm = it.course.term,
                courseYear = it.course.year,
                courseRole = it.role,
                status = it.course.status,
                membershipStatus = it.lifecycleStatus,
                membershipError = it.lastError?.let {
                    "강의 참여 환경 처리 중 오류가 발생했습니다. 잠시 후 재시도하거나 관리자에게 문의해주세요."
                }
            )
        }
    }

    // 조교 전용 강의 정보 조회
    @Transactional(readOnly = true)
    fun getUserAssistantCourses(email: String): List<UserCoursesDto> {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("User not found with email: $email")

        // Repository를 사용해서 직접 ASSISTANT 역할인 강의만 조회
        val assistantCourses = userCoursesRepository.findByUserEmailAndRole(email, RoleType.ASSISTANT)
            .filter(::isVisibleMembership)

        return assistantCourses.map {
            UserCoursesDto(
                courseId = it.course.id,
                courseName = it.course.name,
                courseProfessor = it.course.professor,
                courseClss = it.course.clss,
                courseTerm = it.course.term,
                courseYear = it.course.year,
                courseRole = it.role,
                status = it.course.status,
                membershipStatus = it.lifecycleStatus,
                membershipError = it.lastError?.let {
                    "강의 참여 환경 처리 중 오류가 발생했습니다. 잠시 후 재시도하거나 관리자에게 문의해주세요."
                }
            )
        }
    }

    // 유저별 JCode 정보 조회
    @Transactional(readOnly = true)
    fun getUserJcodes(email: String): List<JCodeDto> {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: $email")

        return jcodeRepository.findByUserId(user.id)
            .filter { it.lifecycleStatus != JcodeLifecycleStatus.ARCHIVED }
            .map {
            JCodeDto(
                jcodeId = it.id,
                courseName = it.course.name,
                status = it.lifecycleStatus,
                observedStatus = it.observedStatus,
                observedReason = it.observedReason,
                lastObservedAt = it.lastObservedAt,
                jcodeUrl = it.jcodeUrl,
                assignmentId = it.assignment?.id,
                lastError = it.lastError?.let {
                    "JCode 환경 처리 중 오류가 발생했습니다. 잠시 후 재시도하거나 관리자에게 문의해주세요."
                }
            )
        }
    }

    // 유저별 참가 강의의 과제 및 JCode 정보 조회
    @Transactional(readOnly = true)
    fun getUserCoursesWithDetails(email: String): List<UserCourseDetailsDto> {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: $email")

        return userCoursesRepository.findByUserId(user.id)
            .filter(::isVisibleMembership)
            .map {
            val assignments = assignmentRepository.findByCourseId(it.course.id)
            val jcode = jcodeRepository.findFirstByUserIdAndCourseIdAndSnapshotAndAssignmentIsNullAndLifecycleStatusNotOrderByIdDesc(
                user.id, it.course.id, false, JcodeLifecycleStatus.ARCHIVED
            )

            UserCourseDetailsDto(
                courseId = it.course.id,
                courseName = it.course.name,
                courseProfessor = it.course.professor,
                courseClss = it.course.clss,
                courseTerm = it.course.term,
                courseYear = it.course.year,
                hwCount = it.course.hwCount,
                pracEnabled = it.course.pracEnabled,
                pracCount = it.course.pracCount,
                status = it.course.status,
                environmentProfile = it.course.environmentProfile,
                workspaceScope = it.course.workspaceScope,
                courseRole = it.role,
                membershipStatus = it.lifecycleStatus,
                membershipError = it.lastError?.let {
                    "강의 참여 환경 처리 중 오류가 발생했습니다. 잠시 후 재시도하거나 관리자에게 문의해주세요."
                },
                assignments = assignments.map { assignment ->
                    AssignmentDto(
                        assignmentId = assignment.id,
                        assignmentName = assignment.name,
                        assignmentDescription = assignment.description,
                        dirName = assignment.workspaceKey,
                        workspaceKey = assignment.workspaceKey,
                        hasStarterCode = assignment.hasStarterCode,
                        lifecycleStatus = assignment.lifecycleStatus,
                        scheduleStatus = assignment.scheduleStatus,
                        lastError = assignment.lastError?.let {
                            "과제 환경 처리 중 오류가 발생했습니다. 잠시 후 재시도하거나 관리자에게 문의해주세요."
                        },
                        archiveRetentionDays = assignment.archiveRetentionDays,
                        archivedAt = assignment.archivedAt?.toString(),
                        finalizedAt = assignment.finalizedAt?.toString(),
                        kickoffDate = assignment.kickoffDate,
                        deadlineDate = assignment.deadlineDate,
                        createdAt = assignment.createdAt.toString(),
                        updatedAt = assignment.updatedAt.toString()
                    )
                },
                jcodeUrl = jcode?.jcodeUrl
            )
        }
    }

    private fun isVisibleMembership(membership: UserCourses): Boolean =
        membership.lifecycleStatus != MembershipStatus.ARCHIVED &&
            membership.course.status !in setOf(CourseStatus.ENDED, CourseStatus.ARCHIVED)

    // 유저 강의 가입
    @Transactional
    fun joinCourse(email: String, courseKey: String): Long {
        // 입력된 courseKey가 "code-clss-randomPart" 형식인지 확인하고, code와 clss 추출
        val parts = courseKey.split("-")
        if (parts.size < 3) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid course key format")
        }
        val infrastructureKey = parts[0]
        val courseClss = parts[1].toIntOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid class value")

        val courses = courseRepository.findByInfrastructureKeyAndClss(infrastructureKey, courseClss)
        if (courses.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found")
        }

        // 입력된 평문 courseKey와 DB에 저장된 해시된 courseKey를 비교하여 일치하는 강의 선택
        val course = courses.firstOrNull { passwordEncoder.matches(courseKey, it.courseKey) }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect course key")

        if (course.status != CourseStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE 상태의 강의에만 가입할 수 있습니다.")
        }

        // 사용자 조회
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val existingMembership = userCoursesRepository.findByUserIdAndCourseId(user.id, course.id)
        if (existingMembership != null) {
            if (existingMembership.lifecycleStatus != MembershipStatus.ARCHIVED) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "User already enrolled in this course")
            }
            existingMembership.role = RoleType.STUDENT
            existingMembership.lifecycleStatus = MembershipStatus.PROVISIONING
            existingMembership.archivedAt = null
            existingMembership.lastError = null
            userCoursesRepository.save(existingMembership)
            redisService.removeUserFromCourseManagerList(course.infrastructureKey, course.clss, email)
            workspaceOperationStore.enqueue(
                WorkspaceOperationTarget.MEMBERSHIP,
                existingMembership.id,
                WorkspaceOperationAction.PROVISION_MEMBERSHIP
            )
            return course.id
        }

        // 참가 코드는 학생으로 가입하는 경로다. 교수/조교 관계는 수업별로 별도 부여한다.
        val role = RoleType.STUDENT

        // UserCourses 엔티티 저장
        val userCourse = UserCourses(
            user = user,
            course = course,
            role = role,
            lifecycleStatus = MembershipStatus.PROVISIONING
        )
        try { // 여러 요청이 동시에 들어와 db unique 제약조건을 위반했을 시 처리
            userCoursesRepository.saveAndFlush(userCourse)
        } catch (ex: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User already enrolled in this course")
        }
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.MEMBERSHIP,
            userCourse.id,
            WorkspaceOperationAction.PROVISION_MEMBERSHIP
        )

        // 가입한 강의의 courseId 반환
        return course.id
    }

    // 유저 강의 탈퇴 (연관된 정보 삭제)
    @Transactional
    fun leaveCourse(courseId: Long, email: String, token: String): Long {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val userCourse = userCoursesRepository.findByUserIdAndCourseId(user.id, course.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not enrolled in this course")

        if (userCourse.lifecycleStatus in setOf(MembershipStatus.DELETE_PENDING, MembershipStatus.ARCHIVED)) return course.id
        userCourse.lifecycleStatus = MembershipStatus.DELETE_PENDING
        userCourse.lastError = null
        userCoursesRepository.save(userCourse)
        jcodeRepository.findAllByUserCourse(userCourse).forEach { jcode ->
            if (jcode.lifecycleStatus != JcodeLifecycleStatus.ARCHIVED) {
                jcode.lifecycleStatus = JcodeLifecycleStatus.DELETE_PENDING
                jcode.lastError = null
                jcodeRepository.save(jcode)
                redisService.deleteJcodeRoute(jcode.id)
            }
        }
        redisService.deleteUserCourseAccess(email, course.infrastructureKey, course.clss)
        redisService.removeUserFromCourseManagerList(course.infrastructureKey, course.clss, email)
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.MEMBERSHIP, userCourse.id, WorkspaceOperationAction.DELETE_MEMBERSHIP
        )

        // 탈퇴한 강의의 courseId 반환
        return course.id
    }

    @Transactional
    fun retryMembership(courseId: Long, email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val membership = userCoursesRepository.findByUserIdAndCourseId(user.id, courseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not enrolled in this course")
        if (membership.lifecycleStatus !in setOf(MembershipStatus.PROVISION_FAILED, MembershipStatus.DELETE_FAILED)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "현재 가입 정보에 실패한 작업이 없습니다.")
        }
        if (!workspaceOperationStore.retry(WorkspaceOperationTarget.MEMBERSHIP, membership.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "재시도할 실패 작업이 없습니다.")
        }
    }


    ///////////////////   관리자용   //////////////////////////

    // 전체 유저 조회
    @Transactional(readOnly = true)
    fun getAllUsers(): List<UserInfoDto> {
        return userRepository.findAll().map { user ->
            UserInfoDto(
                userId = user.id,
                name = user.name,
                email = user.email,
                role = user.role,
                studentNum = user.studentNum,
            )
        }
    }

    // 특정 유저 조회
    @Transactional(readOnly = true)
    fun getUserById(userId: Long): UserDto? {
        val user = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId")
        return user.toDto()
    }

    // 유저 역할 업데이트
    @Transactional
    fun updateUserRole(email: String, userId: Long, newRole: RoleType, courseId: Long?) {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")

        // 대상 유저 조회
        val targetUser = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId")

        // 전역 ASSISTANT 설정 차단: ASSISTANT는 수업별(courseId 필수)로만 부여 가능
        if (newRole == RoleType.ASSISTANT && courseId == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ASSISTANT 권한은 특정 강의에 대해서만 설정할 수 있습니다.")
        }

        if (courseId != null) {
            if (currentUser.role != RoleType.ADMIN) {
                val actorMembership = userCoursesRepository.findByUserIdAndCourseId(currentUser.id, courseId)
                    ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의에 소속되어 있지 않습니다.")
                if (actorMembership.role != RoleType.PROFESSOR) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 강의의 담당 교수 권한이 없습니다.")
                }
                if (newRole !in listOf(RoleType.STUDENT, RoleType.ASSISTANT)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "교수는 해당 수업의 학생/조교 권한만 변경할 수 있습니다.")
                }
            }

            val userCourse = userCoursesRepository.findByUserIdAndCourseId(targetUser.id, courseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not enrolled in this course")

            val course = userCourse.course
            // 새 역할이 ASSISTANT, PROFESSOR로 설정되었을 때는 강의의 관리자로 등록 (redis)
            if (newRole == RoleType.ASSISTANT || newRole == RoleType.PROFESSOR) {
                redisService.addUserToCourseManagerList(course.infrastructureKey, course.clss, targetUser.email)
            }
            // 새 역할이 STUDENT로 설정되었을 때는 강의의 관리자에서 등록 해제 (redis) + user_courses 엔티티의 role도 STUDENT로 업데이트
            else if (newRole == RoleType.STUDENT) {
                redisService.removeUserFromCourseManagerList(course.infrastructureKey, course.clss, targetUser.email)
            }

            userCourse.role = newRole
            userCoursesRepository.save(userCourse)
        } else {
            if (currentUser.role != RoleType.ADMIN) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "전역 권한은 관리자만 변경할 수 있습니다.")
            }
            targetUser.role = newRole
            userRepository.save(targetUser)
        }
    }

    // 유저 삭제
    @Transactional
    fun deleteUser(userId: Long) {
        val user = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId")
        userRepository.delete(user)
    }

    // 유저 강의 탈퇴 (연관된 정보 삭제)
    @Transactional
    fun chaseOutCourse(userId: Long, courseId: Long, email: String, token: String): Long {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You're Info not found")

        val user = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (currentUser.role != RoleType.ADMIN) {
            val currentUserCourse = userCoursesRepository.findByUserIdAndCourseId(currentUser.id, course.id)
                ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 강의에 대한 권한이 없습니다.")
            if (currentUserCourse.role != RoleType.PROFESSOR) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 강의의 담당 교수 권한이 없습니다.")
            }
        }

        val userCourse = userCoursesRepository.findByUserIdAndCourseId(user.id, course.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not enrolled in this course")

        if (userCourse.lifecycleStatus in setOf(MembershipStatus.DELETE_PENDING, MembershipStatus.ARCHIVED)) return course.id
        userCourse.lifecycleStatus = MembershipStatus.DELETE_PENDING
        userCourse.lastError = null
        userCoursesRepository.save(userCourse)
        jcodeRepository.findAllByUserCourse(userCourse).forEach { jcode ->
            if (jcode.lifecycleStatus != JcodeLifecycleStatus.ARCHIVED) {
                jcode.lifecycleStatus = JcodeLifecycleStatus.DELETE_PENDING
                jcode.lastError = null
                jcodeRepository.save(jcode)
                redisService.deleteJcodeRoute(jcode.id)
            }
        }
        redisService.deleteUserCourseAccess(user.email, course.infrastructureKey, course.clss)
        redisService.removeUserFromCourseManagerList(course.infrastructureKey, course.clss, user.email)
        workspaceOperationStore.enqueue(
            WorkspaceOperationTarget.MEMBERSHIP, userCourse.id, WorkspaceOperationAction.DELETE_MEMBERSHIP
        )

        // 탈퇴한 강의의 courseId 반환
        return course.id
    }

}
