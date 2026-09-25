# MAIFlow for JetBrains IDEs

MAIFlow is a platform-only IntelliJ plugin for viewing and managing MAIFlow tasks without leaving the IDE. It provides a `MAIFlow` tool window, task creation and editing, flow/project filters, IDE-project mapping, and browser links to full MAIFlow pages.

## Setup

1. Open **Settings | Tools | MAIFlow**.
2. Keep the default endpoint (`https://app.maiflow.org/api/mcp`) or enter a development endpoint such as `http://localhost:3000/api/mcp`.
3. Create an API token in **MAIFlow Profile → API access** and paste it into the password field.
4. Apply the settings and select **Test Connection**.
5. Open the `MAIFlow` tool window and use **Map project** to select the flow used for new tasks.

The token is stored only in the IntelliJ Platform credential store. The server URL and connection metadata are application settings; flow/project mapping IDs are user-specific project workspace state. Task descriptions, source code, selected editor text, and API responses are not persisted by the plugin.

MAIFlow MCP requires an active Personal or Team plan. Rotating or revoking the token invalidates all clients using it, including this plugin and other MCP clients.

## JetBrains AI Assistant

The plugin is independent of JetBrains AI Assistant. The MAIFlow settings page includes a redacted copyable MCP configuration template and a link to JetBrains’ MCP documentation. Configure each integration independently with the same token; this plugin never reads or changes AI Assistant’s private configuration.

## Development

The plugin targets IntelliJ IDEA 2025.3.6 and build 253+, uses Java 21 and Kotlin, and depends only on `com.intellij.modules.platform` plus its pinned JSON parser. Useful Gradle tasks:

```text
./gradlew check
./gradlew verifyPlugin
./gradlew buildPlugin
```

Optional read-only integration checks can be enabled by supplying `MAIFLOW_PLUGIN_E2E_URL` and `MAIFLOW_PLUGIN_E2E_TOKEN` in the local environment. They are intentionally not part of the plugin configuration or project state.
