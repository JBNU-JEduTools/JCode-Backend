package org.jbnu.jdevops.jcodeportallogin.util

import org.jbnu.jdevops.jcodeportallogin.entity.User

object WorkspaceNaming {
    private const val GENERAL_WORKSPACE_SUFFIX = "의 JCode.code-workspace"
    private val unsafeFilenameCharacters = Regex("[\\\\/:*?\"<>|]")

    fun displayName(user: User): String {
        val preferred = user.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: user.email.substringBefore('@').trim()
        return unsafeFilenameCharacters.replace(
            preferred.filterNot(Char::isISOControl), "_"
        ).trim().trim('.').take(50)
            .ifEmpty { user.studentNum?.toString() ?: "JCode" }
    }

    fun generalWorkspaceFile(user: User): String = "${displayName(user)}$GENERAL_WORKSPACE_SUFFIX"

    fun assignmentWorkspaceFile(assignmentName: String): String {
        val safeName = unsafeFilenameCharacters.replace(
            assignmentName.trim().filterNot(Char::isISOControl), "_"
        ).trim().trim('.').take(50).ifEmpty { "과제" }
        return "$safeName.code-workspace"
    }
}
