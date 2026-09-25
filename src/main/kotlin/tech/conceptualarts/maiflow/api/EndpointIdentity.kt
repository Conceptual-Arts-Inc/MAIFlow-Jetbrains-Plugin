package tech.conceptualarts.maiflow.api

import java.net.URI

/** Validation and canonicalization for the user-configured MCP endpoint. */
object EndpointIdentity {
    const val DEFAULT_ENDPOINT = "https://app.maiflow.org/api/mcp"

    fun normalize(raw: String): String {
        val value = raw.trim()
        require(value.isNotEmpty()) { "MAIFlow server URL cannot be empty." }

        val uri = try {
            URI(value)
        } catch (error: Exception) {
            throw IllegalArgumentException("MAIFlow server URL is not valid.", error)
        }
        val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("MAIFlow server URL must include http or https.")
        require(scheme == "https" || scheme == "http") { "MAIFlow server URL must use HTTP or HTTPS." }
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "MAIFlow server URL must not contain credentials, a query, or a fragment."
        }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("MAIFlow server URL must include a host.")
        require(scheme == "https" || isLoopback(host)) {
            "HTTPS is required for non-local MAIFlow endpoints."
        }

        val port = uri.port
        val normalizedPort = if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) -1 else port
        val path = uri.path.trimEnd('/').ifEmpty { "/api/mcp" }
        val authority = if (host.contains(':')) "[$host]" else host
        return buildString {
            append(scheme).append("://").append(authority)
            if (normalizedPort >= 0) append(':').append(normalizedPort)
            append(path)
        }
    }

    fun origin(endpoint: String): String {
        val normalized = normalize(endpoint)
        val uri = URI(normalized)
        val host = uri.host ?: error("Normalized endpoint has no host")
        val authority = if (host.contains(':')) "[$host]" else host
        val port = uri.port
        val defaultPort = (uri.scheme == "https" && port == 443) || (uri.scheme == "http" && port == 80)
        return buildString {
            append(uri.scheme).append("://").append(authority)
            if (port >= 0 && !defaultPort) append(':').append(port)
        }
    }

    fun profileUrl(endpoint: String): String = "${origin(endpoint)}/profile"

    fun isLoopback(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0:0:0:0:0:0:0:1"
}
