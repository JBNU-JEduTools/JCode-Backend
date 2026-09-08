package org.jbnu.jdevops.jcodeportallogin.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.web.server.ResponseStatusException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `unreadable request body is returned as sanitized bad request`() {
        val request = MockHttpServletRequest("POST", "/api/courses")

        val response = handler.handleUnreadableMessage(
            HttpMessageNotReadableException("internal enum parser detail", MockHttpInputMessage(byteArrayOf())),
            request
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_REQUEST_BODY", response.body?.code)
        assertEquals("입력 정보를 확인해주세요.", response.body?.message)
        assertFalse(response.body?.message.orEmpty().contains("enum parser"))
    }

    @Test
    fun `server exception detail is not returned to client`() {
        val request = MockHttpServletRequest("POST", "/api/courses")
        val response = handler.handleResponseStatusException(
            ResponseStatusException(HttpStatus.BAD_GATEWAY, "jdbc password=secret internal/path"),
            request
        )

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        assertFalse(response.body!!.message.contains("secret"))
        assertFalse(response.body!!.toString().contains("internal/path"))
        assertNotNull(response.headers.getFirst("X-Request-ID"))
    }

    @Test
    fun `actionable conflict reason remains visible`() {
        val request = MockHttpServletRequest("POST", "/api/courses")
        val response = handler.handleResponseStatusException(
            ResponseStatusException(HttpStatus.CONFLICT, "같은 강의 코드와 분반의 강의가 이미 존재합니다."),
            request
        )

        assertEquals("같은 강의 코드와 분반의 강의가 이미 존재합니다.", response.body!!.message)
        assertEquals("CONFLICT", response.body!!.code)
        assertTrue(response.body!!.requestId.isNotBlank())
    }
}
