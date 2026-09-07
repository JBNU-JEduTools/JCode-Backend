package org.jbnu.jdevops.jcodeportallogin.util

import org.assertj.core.api.Assertions.assertThat
import org.jbnu.jdevops.jcodeportallogin.entity.RoleType
import org.jbnu.jdevops.jcodeportallogin.entity.User
import org.junit.jupiter.api.Test

class WorkspaceNamingTest {
    @Test
    fun `uses a safe user name for the general workspace file`() {
        val user = User(
            email = "student@example.com",
            name = "홍/길동",
            studentNum = 20260001,
            role = RoleType.STUDENT
        )

        assertThat(WorkspaceNaming.displayName(user)).isEqualTo("홍_길동")
        assertThat(WorkspaceNaming.generalWorkspaceFile(user))
            .isEqualTo("홍_길동의 JCode.code-workspace")
    }

    @Test
    fun `falls back to the email local part`() {
        val user = User(
            email = "student@example.com",
            studentNum = 20260001,
            role = RoleType.STUDENT
        )

        assertThat(WorkspaceNaming.generalWorkspaceFile(user))
            .isEqualTo("student의 JCode.code-workspace")
    }

    @Test
    fun `uses the assignment name without exposing its storage key`() {
        assertThat(WorkspaceNaming.assignmentWorkspaceFile("정렬/검색"))
            .isEqualTo("정렬_검색.code-workspace")
    }
}
