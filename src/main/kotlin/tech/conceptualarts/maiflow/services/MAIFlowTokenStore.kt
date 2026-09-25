package tech.conceptualarts.maiflow.services

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import tech.conceptualarts.maiflow.api.EndpointIdentity

/** PasswordSafe is intentionally the only persistence boundary for the API token. */
@Service(Service.Level.APP)
class MAIFlowTokenStore {
    fun readToken(endpoint: String): String? {
        val normalized = EndpointIdentity.normalize(endpoint)
        return PasswordSafe.instance.get(attributes(normalized))?.getPasswordAsString()?.takeIf { it.isNotBlank() }
    }

    fun saveToken(endpoint: String, token: String) {
        val normalized = EndpointIdentity.normalize(endpoint)
        PasswordSafe.instance.set(attributes(normalized), Credentials(null, token))
    }

    fun clearToken(endpoint: String) {
        val normalized = EndpointIdentity.normalize(endpoint)
        PasswordSafe.instance.set(attributes(normalized), null)
    }

    fun hasToken(endpoint: String): Boolean = readToken(endpoint) != null

    fun redactedPrefix(token: String): String = token.take(12) + if (token.length > 12) "…" else ""

    private fun attributes(endpoint: String): CredentialAttributes =
        CredentialAttributes("MAIFlow MCP token [$endpoint]")

    companion object {
        fun getInstance(): MAIFlowTokenStore =
            ApplicationManager.getApplication().getService(MAIFlowTokenStore::class.java)
    }
}
