package tech.conceptualarts.maiflow.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpClientTest {
    @Test
    fun initializeAndCapabilitiesUseJsonRpcAndExtractStructuredRoutes() {
        val transport = McpTransport { _, _, request ->
            val method = JsonParser.parseString(request).asJsonObject.get("method").asString
            when (method) {
                "initialize" -> McpHttpResponse(200, """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","serverInfo":{"name":"maiflow","version":"1"}}}""")
                else -> McpHttpResponse(200, """{"jsonrpc":"2.0","id":2,"result":{"structuredContent":{"endpoint":"/api/mcp","routes":[{"path":"/api/workspace/tasks","methods":["GET"]}]}}}""")
            }
        }
        val client = McpClient(transport)
        assertEquals("2025-06-18", client.initialize("https://app.maiflow.org/api/mcp", "mf_live_test", "1").protocolVersion)
        assertTrue(client.capabilities("https://app.maiflow.org/api/mcp", "mf_live_test").allows("GET", "/api/workspace/tasks"))
    }
}
