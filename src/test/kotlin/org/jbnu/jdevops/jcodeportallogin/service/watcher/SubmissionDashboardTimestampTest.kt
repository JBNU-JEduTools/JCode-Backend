package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SubmissionDashboardTimestampTest {
    @Test
    fun `UTC watcher timestamp is converted to KST`() {
        assertEquals(
            LocalDateTime.parse("2026-09-04T15:14:48.232332"),
            parseWatcherTimestamp("2026-09-04T06:14:48.232332Z")
        )
    }

    @Test
    fun `offset watcher timestamp is converted to KST`() {
        assertEquals(
            LocalDateTime.parse("2026-09-04T15:14:48.232332"),
            parseWatcherTimestamp("2026-09-04T08:14:48.232332+02:00")
        )
    }

    @Test
    fun `legacy local timestamp remains unchanged`() {
        assertEquals(
            LocalDateTime.parse("2026-09-04T15:14:48.232332"),
            parseWatcherTimestamp("2026-09-04T15:14:48.232332")
        )
    }
}
