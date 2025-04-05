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
    private val redisService: RedisService
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

            // 새 사용자 저장
            val user = userRepository.save(
                User(
                    email = registerUserDto.email,
                    role = registerUserDto.role,  // 기본적으로 학생 역할 부여
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

        val userCourses = user.courses
        return userCourses.map {
            UserCoursesDto(
                courseId = it.course.id,
                courseName = it.course.name,
                courseCode = it.course.code,
                courseProfessor = it.course.professor,
                courseClss = it.course.clss,
                courseTerm = it.course.term,
                courseYear = it.course.year
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

        return assistantCourses.map {
            UserCoursesDto(
                courseId = it.course.id,
                courseName = it.course.name,
                courseCode = it.course.code,
                courseProfessor = it.course.professor,
                courseClss = it.course.clss,
                courseTerm = it.course.term,
                courseYear = it.course.year
            )
        }
    }

    // 유저별 JCode 정보 조회
    @Transactional(readOnly = true)
    fun getUserJcodes(email: String): List<JCodeDto> {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: $email")

        return jcodeRepository.findByUserId(user.id).map {
            JCodeDto(
                jcodeId = it.id,
                courseName = it.course.name
            )
        }
    }

    // 유저별 참가 강의의 과제 및 JCode 정보 조회
    @Transactional(readOnly = true)
    fun getUserCoursesWithDetails(email: String): List<UserCourseDetailsDto> {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: $email")

        return userCoursesRepository.findByUserId(user.id).map {
            val assignments = assignmentRepository.findByCourseId(it.course.id)
            val jcode = jcodeRepository.findByUserIdAndCourseIdAndSnapshot(user.id, it.course.id, false)

            UserCourseDetailsDto(
                courseId = it.course.id,
                courseName = it.course.name,
                courseCode = it.course.code,
                courseProfessor = it.course.professor,
                courseClss = it.course.clss,
                courseTerm = it.course.term,
                courseYear = it.course.year,
                assignments = assignments.map { assignment ->
                    AssignmentDto(
                        assignmentId = assignment.id,
                        assignmentName = assignment.name,
                        assignmentDescription = assignment.description,
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

    // 유저 강의 가입
    @Transactional
    fun joinCourse(email: String, courseKey: String): Long {
        // 입력된 courseKey가 "code-clss-randomPart" 형식인지 확인하고, code와 clss 추출
        val parts = courseKey.split("-")
        if (parts.size < 3) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid course key format")
        }
        val courseCode = parts[0]
        val courseClss = parts[1].toIntOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid class value")

        // courseCode와 courseClss를 기준으로 강의 목록 조회
        val courses = courseRepository.findByCodeAndClss(courseCode, courseClss)
        if (courses.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found")
        }

        // 입력된 평문 courseKey와 DB에 저장된 해시된 courseKey를 비교하여 일치하는 강의 선택
        val course = courses.firstOrNull { passwordEncoder.matches(courseKey, it.courseKey) }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect course key")

        // 사용자 조회
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        // 중복 가입 방지
        if (userCoursesRepository.existsByUserIdAndCourseId(user.id, course.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User already enrolled in this course")
        }

        // 조교는 가입 시 기본적으로 STUDENT로 가입  ->  추후에 관리자/교수가 권한 승격 시켜줌
        var role: RoleType? = null
        if (user.role == RoleType.ASSISTANT) role = RoleType.STUDENT
        else role = user.role

        // UserCourses 엔티티 저장
        val userCourse = UserCourses(
            user = user,
            course = course,
            role = role
        )
        try { // 여러 요청이 동시에 들어와 db unique 제약조건을 위반했을 시 처리
            userCoursesRepository.save(userCourse)
        } catch (ex: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User already enrolled in this course")
        }
        
        // 교수는 강의 관리자에 추가
        if (role == RoleType.PROFESSOR) {
            // DB 저장 후 Redis 데이터 동기화: 강의 코드와 강의 분반(clss)을 함께 사용
            val storedUserCourse = userCoursesRepository.findByUserIdAndCourseCode(user.id, course.code)
            if (storedUserCourse != null) {
                redisService.addUserToCourseManagerList(course.code, course.clss, email)
            }   
        }

        // 가입한 강의의 courseId 반환
        return course.id
    }

    // 유저 강의 탈퇴 (연관된 정보 삭제)
    @Transactional
    fun leaveCourse(courseId: Long, email: String): Long {
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val userCourse = userCoursesRepository.findByUserIdAndCourseId(user.id, course.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not enrolled in this course")

        // 해당 강의에서 사용된 JCode 삭제
        jcodeRepository.findByUserCourse(userCourse)?.let {
            jcodeRepository.delete(it)
        }

        // UserCourses에서 유저 삭제 (강의 탈퇴)
        userCoursesRepository.delete(userCourse)

        // Redis에서 해당 강의의 참여자 목록에서 해당 유저(email) 제거
        redisService.removeUserFromCourseManagerList(course.code, course.clss, email)

        // 탈퇴한 강의의 courseId 반환
        return course.id
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

        when (currentUser.role) {
            RoleType.ADMIN -> {}  // ADMIN은 모든 권한 설정 가능
            RoleType.PROFESSOR -> {
                if (courseId == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "권한을 변경할 강의가 지정되지 않았습니다.")
                val course = courseRepository.findById(courseId)
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }
                // 현재 교수 사용자가 Redis에 해당 강의 관리자로 등록되어 있는지 확인
                if (!redisService.isUserInCourseManagers(course.code, course.clss, currentUser.email)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "현재 교수로 등록되어 있지 않아 권한이 없습니다.")
                }
                // 대상 유저의 기존 role과 변경할 새 role은 STUDENT 또는 ASSISTANT여야 함
                if (newRole !in listOf(RoleType.STUDENT, RoleType.ASSISTANT) || targetUser.role !in listOf(RoleType.STUDENT, RoleType.ASSISTANT)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "PROFESSOR는 STUDENT 또는 ASSISTANT의 권한만 변경할 수 있습니다.")
                }
            }
            else -> {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 권한으로는 유저 권한 변경이 불가능합니다.")
            }
        }

        // courseId가 전달되었을 경우, 해당 강의에 대해서만 Redis 업데이트 수행
        if (courseId != null) {
            val userCourse = userCoursesRepository.findByUserIdAndCourseId(targetUser.id, courseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not enrolled in this course")

            val course = userCourse.course
            // 새 역할이 ASSISTANT, PROFESSOR로 설정되었을 때는 강의의 관리자로 등록 (redis)
            if (newRole == RoleType.ASSISTANT || newRole == RoleType.PROFESSOR) {
                redisService.addUserToCourseManagerList(course.code, course.clss, targetUser.email)
            }
            // 새 역할이 STUDENT로 설정되었을 때는 강의의 관리자에서 등록 해제 (redis) + user_courses 엔티티의 role도 STUDENT로 업데이트
            else if (newRole == RoleType.STUDENT) {
                redisService.removeUserFromCourseManagerList(course.code, course.clss, targetUser.email)
            }

            userCourse.role = newRole
            userCoursesRepository.save(userCourse)

            // 대상 유저의 역할 업데이트 후 저장 (ASSISTANT는 하나라도 ASSISTANT를 가지고 있을 시 업데이트 X)
            if (newRole != RoleType.STUDENT || userCoursesRepository.findByUserEmailAndRole(targetUser.email, targetUser.role).isEmpty()) {
                targetUser.role = newRole
                userRepository.save(targetUser)
            }
        } else {
            // courseId가 null이면 모든 가입 강의에 대해 업데이트 (STUDENT, PROFESSOR만 해당)
            targetUser.courses.forEach { userCourse ->
                val course = userCourse.course
                if (newRole == RoleType.STUDENT) {
                    redisService.removeUserFromCourseManagerList(course.code, course.clss, targetUser.email)
                } else if (newRole == RoleType.PROFESSOR) {
                    redisService.addUserToCourseManagerList(course.code, course.clss, targetUser.email)
                }

                userCourse.role = newRole
                userCoursesRepository.save(userCourse)
            }

            // 대상 유저의 역할 업데이트 후 저장
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
    fun chaseOutCourse(userId: Long, courseId: Long, email: String): Long {
        val currentUser = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You're Info not found")

        val user = userRepository.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (currentUser.role != RoleType.ADMIN) {
            val currentUserCourse = userCoursesRepository.findByUserIdAndCourseId(user.id, course.id)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "이 강의에 대한 권한이 없습니다.")
        }

        val userCourse = userCoursesRepository.findByUserIdAndCourseId(user.id, course.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not enrolled in this course")

        // 해당 강의에서 사용된 JCode 삭제
        jcodeRepository.findByUserCourse(userCourse)?.let {
            jcodeRepository.delete(it)
        }

        // UserCourses에서 유저 삭제 (강의 탈퇴)
        userCoursesRepository.delete(userCourse)

        // Redis에서 해당 강의의 참여자 목록에서 해당 유저(email) 제거
        redisService.removeUserFromCourseManagerList(course.code, course.clss, user.email)

        // 탈퇴한 강의의 courseId 반환
        return course.id
    }

}
