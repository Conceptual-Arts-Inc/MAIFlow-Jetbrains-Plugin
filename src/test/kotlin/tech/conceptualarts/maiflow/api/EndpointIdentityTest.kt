package tech.conceptualarts.maiflow.api

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointIdentityTest {
    @Test
    fun normalizesTrailingSlashAndDefaultPort() {
        assertEquals("https://app.maiflow.org/api/mcp", EndpointIdentity.normalize(" HTTPS://APP.MAIFLOW.ORG:443/api/mcp/// "))
    }

    @Test
    fun permitsLoopbackHttpOnly() {
        assertEquals("http://localhost:3000/api/mcp", EndpointIdentity.normalize("http://localhost:3000/api/mcp/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPlainHttpForRemoteHosts() {
        EndpointIdentity.normalize("http://maiflow.example/api/mcp")
    }

    @Test
    fun constructsProfileUrlFromOrigin() {
        assertEquals("https://app.maiflow.org/profile", EndpointIdentity.profileUrl("https://app.maiflow.org/api/mcp"))
    }
}
