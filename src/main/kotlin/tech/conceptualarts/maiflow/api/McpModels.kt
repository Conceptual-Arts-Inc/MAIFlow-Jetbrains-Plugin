package tech.conceptualarts.maiflow.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class McpRoute(val path: String, val methods: Set<String>, val description: String = "")

data class McpCapabilityCatalog(val endpoint: String, val routes: List<McpRoute>) {
    fun allows(method: String, path: String): Boolean = routes.any { route ->
        route.methods.contains(method) && route.path.split('/').size == path.split('/').size &&
            route.path.split('/').zip(path.split('/')).all { (catalogSegment, actualSegment) ->
                catalogSegment.startsWith(":") || catalogSegment == actualSegment
            }
    }
}

data class McpToolResult(
    val structuredContent: JsonObject?,
    val textContent: String?,
    val isError: Boolean,
)

internal fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.int(name: String): Int? = get(name)?.takeIf { it.isJsonPrimitive }?.let {
    runCatching { it.asInt }.getOrNull()
}
internal fun JsonObject.bool(name: String): Boolean? = get(name)?.takeIf { it.isJsonPrimitive }?.let {
    runCatching { it.asBoolean }.getOrNull()
}
internal fun JsonObject.objectValue(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.arrayValue(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
internal fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject
