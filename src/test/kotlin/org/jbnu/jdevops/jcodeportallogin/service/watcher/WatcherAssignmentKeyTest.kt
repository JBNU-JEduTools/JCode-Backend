package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.jbnu.jdevops.jcodeportallogin.entity.Assignment
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WatcherAssignmentKeyTest {
    private val course = Course(
        name = "Algorithms",
        code = "alg",
        year = 2026,
        term = 1,
        professor = "Professor",
        clss = 1,
        vnc = false,
        courseKey = "course-key"
    )

    @Test
    fun `uses immutable directory name for watcher hw name`() {
        val assignment = assignment(name = "Updated Title", dirName = "Original Title")

        assertEquals("Original Title", assignment.watcherHwName())
    }

    @Test
    fun `falls back to display name for legacy assignments without dir name`() {
        val assignment = assignment(name = "hw1", dirName = "")

        assertEquals("hw1", assignment.watcherHwName())
    }

    private fun assignment(name: String, dirName: String): Assignment {
        val now = LocalDateTime.of(2026, 6, 29, 0, 0)
        return Assignment(
            course = course,
            name = name,
            description = null,
            dirName = dirName,
            kickoffDate = now,
            deadlineDate = now.plusDays(7)
        )
    }
}
