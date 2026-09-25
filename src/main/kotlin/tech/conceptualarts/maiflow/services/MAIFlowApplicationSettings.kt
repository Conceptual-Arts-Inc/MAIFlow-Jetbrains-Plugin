package tech.conceptualarts.maiflow.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import tech.conceptualarts.maiflow.api.EndpointIdentity

@State(name = "MAIFlowSettings", storages = [Storage("maiflow.xml")])
@Service(Service.Level.APP)
class MAIFlowApplicationSettings : PersistentStateComponent<MAIFlowApplicationSettings.State> {
    data class State(
        var serverUrl: String = EndpointIdentity.DEFAULT_ENDPOINT,
        var tokenPrefix: String = "",
        var lastSuccessfulConnectionAt: Long = 0L,
    )

    private var state = State()

    val serverUrl: String get() = state.serverUrl
    val tokenPrefix: String get() = state.tokenPrefix
    val lastSuccessfulConnectionAt: Long get() = state.lastSuccessfulConnectionAt

    override fun getState(): State = state

    override fun loadState(loadedState: State) {
        XmlSerializerUtil.copyBean(loadedState, state)
        // Be defensive about hand-edited or older settings files. A malformed URL
        // must not make tool-window availability throw during IDE startup.
        state.serverUrl = runCatching { EndpointIdentity.normalize(state.serverUrl) }
            .getOrDefault(EndpointIdentity.DEFAULT_ENDPOINT)
    }

    fun updateServerUrl(value: String) {
        val normalized = EndpointIdentity.normalize(value)
        if (normalized != state.serverUrl) clearConnectionMetadata()
        state.serverUrl = normalized
    }

    fun recordSuccessfulConnection(endpoint: String, prefix: String) {
        state.serverUrl = EndpointIdentity.normalize(endpoint)
        state.tokenPrefix = prefix
        state.lastSuccessfulConnectionAt = System.currentTimeMillis()
    }

    fun setTokenPrefix(prefix: String) {
        if (prefix != state.tokenPrefix) state.lastSuccessfulConnectionAt = 0L
        state.tokenPrefix = prefix
    }

    fun clearConnectionMetadata() {
        state.tokenPrefix = ""
        state.lastSuccessfulConnectionAt = 0L
    }

    fun isConfigured(): Boolean = tokenStoreHasToken()

    private fun tokenStoreHasToken(): Boolean = MAIFlowTokenStore.getInstance().readToken(serverUrl) != null

    companion object {
        fun getInstance(): MAIFlowApplicationSettings =
            ApplicationManager.getApplication().getService(MAIFlowApplicationSettings::class.java)
    }
}
