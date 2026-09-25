package tech.conceptualarts.maiflow.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpModelsTest {
    @Test
    fun routeCatalogMatchesConcretePathParameters() {
        val catalog = McpCapabilityCatalog("/api/mcp", listOf(McpRoute("/api/tasks/:taskId", setOf("PUT"))))
        assertTrue(catalog.allows("PUT", "/api/tasks/task-123"))
        assertFalse(catalog.allows("DELETE", "/api/tasks/task-123"))
        assertFalse(catalog.allows("PUT", "/api/tasks"))
    }
}
