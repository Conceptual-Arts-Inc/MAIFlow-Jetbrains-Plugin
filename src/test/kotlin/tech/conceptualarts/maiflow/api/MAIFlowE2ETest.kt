package tech.conceptualarts.maiflow.api

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, read-only smoke test. It is skipped unless both environment variables are present. */
class MAIFlowE2ETest {
    @Test
    fun initializeCapabilitiesAndWorkspaceRead() {
        val endpoint = System.getenv("MAIFLOW_PLUGIN_E2E_URL")
        val token = System.getenv("MAIFLOW_PLUGIN_E2E_TOKEN")
        assumeTrue("Set MAIFLOW_PLUGIN_E2E_URL and MAIFLOW_PLUGIN_E2E_TOKEN to run this test.", !endpoint.isNullOrBlank() && !token.isNullOrBlank())
        val client = McpClient()
        client.initialize(EndpointIdentity.normalize(endpoint!!), token!!, "test")
        val catalog = client.capabilities(EndpointIdentity.normalize(endpoint), token)
        assertTrue(catalog.allows("GET", "/api/workspace/tasks"))
        val result = client.callTool(
            EndpointIdentity.normalize(endpoint), token, "maiflow_api_request",
            com.google.gson.JsonObject().apply {
                addProperty("method", "GET")
                addProperty("path", "/api/workspace/tasks")
            },
        )
        assertTrue(!result.isError)
    }
}
