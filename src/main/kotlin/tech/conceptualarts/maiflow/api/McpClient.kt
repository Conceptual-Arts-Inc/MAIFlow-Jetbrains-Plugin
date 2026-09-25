package tech.conceptualarts.maiflow.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicLong

data class McpInitialization(val protocolVersion: String, val serverName: String?, val serverVersion: String?)

/** Small JSON-RPC client for MAIFlow's sessionless Streamable HTTP endpoint. */
class McpClient(
    private val transport: McpTransport = McpHttpTransport(),
    private val gson: Gson = Gson(),
) {
    private val log = Logger.getInstance(McpClient::class.java)
    private val requestIds = AtomicLong(0)

    fun initialize(endpoint: String, token: String, pluginVersion: String): McpInitialization {
        val params = JsonObject().apply {
            addProperty("protocolVersion", "2025-06-18")
            add("capabilities", JsonObject())
            add("clientInfo", JsonObject().apply {
                addProperty("name", "maiflow-jetbrains-plugin")
                addProperty("version", pluginVersion)
            })
        }
        val result = callRpc(endpoint, token, "initialize", params)
        val protocolVersion = result.string("protocolVersion")
            ?: throw protocolError("initialize result omitted protocolVersion")
        return McpInitialization(
            protocolVersion = protocolVersion,
            serverName = result.objectValue("serverInfo")?.string("name"),
            serverVersion = result.objectValue("serverInfo")?.string("version"),
        )
    }

    fun capabilities(endpoint: String, token: String): McpCapabilityCatalog {
        val params = JsonObject().apply { addProperty("name", "maiflow_capabilities"); add("arguments", JsonObject()) }
        val result = callRpc(endpoint, token, "tools/call", params)
        val structured = result.objectValue("structuredContent")
        val text = textContent(result)
        val document = structured ?: text?.let { parseObject(it, "capabilities content") }
        val routes = document?.arrayValue("routes") ?: throw protocolError("capabilities result omitted routes")
        val parsedRoutes = routes.mapNotNull { element ->
            val route = element.asObjectOrNull() ?: return@mapNotNull null
            val path = route.string("path") ?: return@mapNotNull null
            val methods = route.arrayValue("methods")?.mapNotNull { method ->
                method.takeIf { it.isJsonPrimitive }?.asString?.uppercase()
            }?.toSet().orEmpty()
            if (methods.isEmpty()) null else McpRoute(path, methods, route.string("description").orEmpty())
        }
        return McpCapabilityCatalog(document.string("endpoint") ?: "/api/mcp", parsedRoutes)
    }

    fun callTool(endpoint: String, token: String, name: String, arguments: JsonObject): McpToolResult {
        val params = JsonObject().apply {
            addProperty("name", name)
            add("arguments", arguments)
        }
        val result = callRpc(endpoint, token, "tools/call", params)
        return McpToolResult(
            structuredContent = result.objectValue("structuredContent"),
            textContent = textContent(result),
            isError = result.bool("isError") == true,
        )
    }

    private fun callRpc(endpoint: String, token: String, method: String, params: JsonObject): JsonObject {
        val id = requestIds.incrementAndGet()
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", method)
            add("params", params)
        }
        val response = transport.post(endpoint, token, gson.toJson(request))
        val root = try {
            parseObject(response.body, "JSON-RPC response")
        } catch (error: MAIFlowApiError) {
            if (response.status !in 200..299) throw MAIFlowApiError.fromStatus(response.status)
            throw error
        }
        if (response.status !in 200..299) {
            throw MAIFlowApiError.fromStatus(response.status, root.objectValue("error")?.string("message"))
        }
        if (root.string("jsonrpc") != "2.0") throw protocolError("JSON-RPC version was not 2.0")
        val responseId = root.get("id")
        if (responseId == null || responseId.isJsonNull || responseId.asLongOrNull() != id) {
            throw protocolError("JSON-RPC response id did not match the request")
        }
        val error = root.objectValue("error")
        if (error != null) {
            val code = error.int("code")
            val status = when (code) {
                -32001 -> 401
                -32002 -> 402
                -32003 -> 403
                else -> null
            }
            if (status != null) throw MAIFlowApiError.fromStatus(status, error.string("message"))
            throw protocolError("JSON-RPC error ${code ?: "unknown"}: ${error.string("message") ?: "request failed"}")
        }
        return root.objectValue("result") ?: throw protocolError("JSON-RPC response omitted result")
    }

    private fun parseObject(body: String, context: String): JsonObject {
        try {
            val parsed = JsonParser.parseString(body)
            if (!parsed.isJsonObject) throw IllegalStateException("response was not an object")
            return parsed.asJsonObject
        } catch (error: Exception) {
            // Deliberately log only shape information. The body may contain task text or a secret echoed by a proxy.
            log.warn("MAIFlow protocol error: $context was malformed (bodyLength=${body.length})")
            throw protocolError("$context was malformed", error)
        }
    }

    private fun textContent(result: JsonObject): String? = result.arrayValue("content")
        ?.firstOrNull { it.asObjectOrNull()?.string("type") == "text" }
        ?.asObjectOrNull()
        ?.string("text")

    private fun com.google.gson.JsonElement.asLongOrNull(): Long? =
        takeIf { isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

    private fun protocolError(detail: String, cause: Throwable? = null): MAIFlowApiError {
        log.warn("MAIFlow protocol error: ${detail.replace(Regex("mf_live_[A-Za-z0-9_-]+"), "mf_live_[redacted]")}")
        return MAIFlowApiError.protocol(detail, cause)
    }
}
