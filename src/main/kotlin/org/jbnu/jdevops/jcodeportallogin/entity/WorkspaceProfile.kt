package org.jbnu.jdevops.jcodeportallogin.entity

enum class CourseEnvironmentProfile {
    ALGORITHM,
    LAB,
    CUSTOM
}

enum class WorkspaceResourceProfile {
    STANDARD,
    HIGH_MEMORY,
    GPU
}

enum class WorkspaceEgressPolicy {
    RESTRICTED,
    PACKAGE_PROXY
}

enum class WorkspaceScope {
    COURSE,
    ASSIGNMENT
}
