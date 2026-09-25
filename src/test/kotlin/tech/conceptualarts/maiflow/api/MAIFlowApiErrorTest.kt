package tech.conceptualarts.maiflow.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MAIFlowApiErrorTest {
    @Test
    fun mapsAccessAndRetryStatuses() {
        val auth = MAIFlowApiError.fromStatus(401, "token mf_live_super-secret")
        assertEquals(MAIFlowErrorKind.AUTHENTICATION, auth.kind)
        assertEquals("MAIFlow token is invalid, revoked, or missing.", auth.message)

        val retry = MAIFlowApiError.fromStatus(503, "temporary failure")
        assertTrue(retry.canRetryRead)
        assertEquals(MAIFlowErrorKind.SERVER, retry.kind)
    }
}
