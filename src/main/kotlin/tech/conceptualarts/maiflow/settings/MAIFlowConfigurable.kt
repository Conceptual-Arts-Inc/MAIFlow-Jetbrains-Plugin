package tech.conceptualarts.maiflow.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ide.CopyPasteManager
import tech.conceptualarts.maiflow.api.EndpointIdentity
import tech.conceptualarts.maiflow.api.MAIFlowApi
import tech.conceptualarts.maiflow.api.MAIFlowApiError
import tech.conceptualarts.maiflow.services.MAIFlowApplicationSettings
import tech.conceptualarts.maiflow.services.MAIFlowTokenStore
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class MAIFlowConfigurable : SearchableConfigurable {
    private val settings = MAIFlowApplicationSettings.getInstance()
    private val tokenStore = MAIFlowTokenStore.getInstance()
    private val api = MAIFlowApi.getInstance()
    private var panel: JPanel? = null
    private var serverField: JBTextField? = null
    private var tokenField: JBPasswordField? = null
    private var statusLabel: JBLabel? = null

    override fun getId(): String = "tech.conceptualarts.maiflow.settings"
    override fun getDisplayName(): @NlsContexts.ConfigurableName String = "MAIFlow"
    override fun getHelpTopic(): String? = "tech.conceptualarts.maiflow.settings"

    override fun createComponent(): JComponent {
        val server = JBTextField()
        val token = JBPasswordField()
        val status = JBLabel()
        serverField = server
        tokenField = token
        statusLabel = status

        val root = JPanel(GridBagLayout()).apply { border = JBUI.Borders.empty(8) }
        var row = 0
        fun addRow(label: JComponent, component: JComponent, hint: String? = null) {
            root.add(label, constraints(0, row, 0.0, GridBagConstraints.NONE))
            root.add(component, constraints(1, row, 1.0, GridBagConstraints.HORIZONTAL).apply { weightx = 1.0 })
            row++
            if (hint != null) {
                root.add(JBLabel(hint).apply { foreground = com.intellij.ui.JBColor.GRAY }, constraints(1, row, 1.0, GridBagConstraints.HORIZONTAL).apply { insets = Insets(0, 0, 6, 0) })
                row++
            }
        }

        addRow(JBLabel("Server URL:"), server, "The MAIFlow MCP endpoint. HTTPS is required except for localhost development endpoints.")
        addRow(JBLabel("API token:"), token, "Stored securely in the IDE credential store. Leave empty to keep the existing token.")

        val buttonPanel = JPanel().apply {
            add(JButton("Test Connection").apply { addActionListener { testConnection() } })
            add(JButton("Open MAIFlow Profile").apply { addActionListener { openProfile() } })
            add(JButton("Copy AI Assistant template").apply { addActionListener { copyAiTemplate() } })
        }
        root.add(buttonPanel, constraints(1, row, 1.0, GridBagConstraints.HORIZONTAL).apply { insets = Insets(6, 0, 6, 0) })
        row++
        root.add(status, constraints(1, row, 1.0, GridBagConstraints.HORIZONTAL))
        row++

        val docs = HyperlinkLabel("Learn about MAIFlow MCP and JetBrains AI Assistant")
        docs.setHyperlinkTarget("https://www.jetbrains.com/help/ai-assistant/mcp.html")
        root.add(docs, constraints(1, row, 1.0, GridBagConstraints.HORIZONTAL).apply { insets = Insets(8, 0, 4, 0) })
        row++
        root.add(JBLabel("Rotating or revoking a MAIFlow token invalidates existing clients, including this plugin and other MCP clients."), constraints(1, row, 1.0, GridBagConstraints.HORIZONTAL).apply { insets = Insets(4, 0, 4, 0) })

        server.text = settings.serverUrl
        status.text = statusText()
        panel = root
        return root
    }

    override fun isModified(): Boolean {
        val server = serverField?.text ?: return false
        val token = tokenField?.password?.concatToString().orEmpty()
        val normalized = runCatching { EndpointIdentity.normalize(server) }.getOrNull()
        return normalized != settings.serverUrl || token.isNotEmpty()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val server = serverField?.text.orEmpty()
        val normalized = try {
            EndpointIdentity.normalize(server)
        } catch (error: IllegalArgumentException) {
            throw ConfigurationException(error.message ?: "Invalid MAIFlow server URL.")
        }
        val token = tokenField?.password?.concatToString().orEmpty()
        if (token.isNotEmpty()) validateToken(token)
        if (normalized != settings.serverUrl) {
            settings.updateServerUrl(normalized)
            api.clearSession()
        }
        if (token.isNotEmpty()) {
            tokenStore.saveToken(normalized, token)
            settings.setTokenPrefix(tokenStore.redactedPrefix(token))
            java.util.Arrays.fill(tokenField?.password ?: CharArray(0), '\u0000')
            tokenField?.text = ""
        }
        updateToolWindowAvailability()
        statusLabel?.text = statusText()
    }

    override fun reset() {
        serverField?.text = settings.serverUrl
        tokenField?.text = ""
        statusLabel?.text = statusText()
    }

    override fun disposeUIResources() {
        panel = null
        serverField = null
        tokenField = null
        statusLabel = null
    }

    private fun testConnection() {
        val server = serverField?.text.orEmpty()
        val token = tokenField?.password?.concatToString().orEmpty().ifEmpty {
            runCatching { tokenStore.readToken(EndpointIdentity.normalize(server)) }.getOrNull().orEmpty()
        }
        val endpoint = try {
            EndpointIdentity.normalize(server)
        } catch (error: IllegalArgumentException) {
            statusLabel?.text = error.message
            return
        }
        if (token.isBlank()) {
            statusLabel?.text = "Enter an API token before testing the connection."
            return
        }
        try {
            validateToken(token)
        } catch (error: ConfigurationException) {
            statusLabel?.text = error.message
            return
        }
        statusLabel?.text = "Testing MAIFlow connection…"
        object : Task.Backgroundable(null, "Testing MAIFlow connection", true) {
            private var result: tech.conceptualarts.maiflow.api.ConnectionTestResult? = null
            private var failure: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    result = api.testConnection(endpoint, token)
                } catch (error: Throwable) {
                    failure = error
                }
            }

            override fun onSuccess() {
                val error = failure
                if (error != null) {
                    statusLabel?.text = errorMessage(error)
                    return
                }
                settings.recordSuccessfulConnection(endpoint, tokenStore.redactedPrefix(token))
                statusLabel?.text = "Connection succeeded."
            }
        }.queue()
    }

    private fun openProfile() {
        val endpoint = runCatching { EndpointIdentity.normalize(serverField?.text.orEmpty()) }.getOrElse { settings.serverUrl }
        BrowserUtil.browse(EndpointIdentity.profileUrl(endpoint))
    }

    private fun updateToolWindowAvailability() {
        val available = settings.isConfigured()
        ProjectManager.getInstance().openProjects.forEach { project ->
            ToolWindowManager.getInstance(project).getToolWindow("MAIFlow")?.setAvailable(available)
        }
    }

    private fun copyAiTemplate() {
        val endpoint = runCatching { EndpointIdentity.normalize(serverField?.text.orEmpty()) }.getOrElse { EndpointIdentity.DEFAULT_ENDPOINT }
        val template = """{
  "mcpServers": {
    "maiflow": {
      "url": "$endpoint",
      "headers": { "Authorization": "Bearer mf_live_<paste-token-from-profile>" }
    }
  }
}"""
        CopyPasteManager.getInstance().setContents(StringSelection(template))
        statusLabel?.text = "Copied a redacted AI Assistant MCP template."
    }

    private fun statusText(): String = buildString {
        if (settings.tokenPrefix.isNotBlank()) append("Token: ${settings.tokenPrefix}. ") else append("No token configured. ")
        if (settings.lastSuccessfulConnectionAt > 0) {
            val formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(settings.lastSuccessfulConnectionAt))
            append("Last successful connection: $formatted")
        } else append("Test the connection after saving your settings.")
    }

    private fun validateToken(token: String) {
        if (!token.startsWith("mf_live_")) throw ConfigurationException("MAIFlow API tokens must start with mf_live_.")
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is MAIFlowApiError -> error.message
        else -> error.message ?: "MAIFlow connection failed."
    }

    private fun constraints(x: Int, y: Int, weightY: Double, fillMode: Int): GridBagConstraints = GridBagConstraints().apply {
        gridx = x; gridy = y; this.weighty = weightY; fill = fillMode
        anchor = GridBagConstraints.WEST
        insets = Insets(4, 4, 4, 8)
    }
}
