package tech.conceptualarts.maiflow.api

import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class McpHttpResponse(val status: Int, val body: String)

fun interface McpTransport {
    fun post(endpoint: String, token: String, json: String): McpHttpResponse
}

/** The only class in the plugin that knows how JSON-RPC is transported over HTTP. */
class McpHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        // Never redirect an authenticated request. A redirect can otherwise send the
        // MAIFlow bearer token to a different host before the caller can inspect it.
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    private val requestTimeout: Duration = Duration.ofSeconds(45),
) : McpTransport {
    private val log = Logger.getInstance(McpHttpTransport::class.java)

    override fun post(endpoint: String, token: String, json: String): McpHttpResponse {
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            McpHttpResponse(response.statusCode(), response.body())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MAIFlowApiError.network("The MAIFlow request was cancelled.", error)
        } catch (error: java.net.http.HttpTimeoutException) {
            throw MAIFlowApiError.network("MAIFlow did not respond before the request timed out.", error)
        } catch (error: java.io.IOException) {
            throw MAIFlowApiError.network("Could not connect to MAIFlow. Check the server URL and your network connection.", error)
        } catch (error: RuntimeException) {
            log.warn("MAIFlow HTTP request could not be created or sent.", error)
            throw MAIFlowApiError.network("Could not connect to MAIFlow.", error)
        }
    }
}
