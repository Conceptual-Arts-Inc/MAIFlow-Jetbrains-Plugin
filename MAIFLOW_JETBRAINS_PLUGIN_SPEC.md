# MAIFlow JetBrains Plugin Specification

Status: Proposed  
Target project: `maiflow-jetbrains-plugin`  
Date: 2026-09-16

## 1. Decision summary

Build a small, platform-only IntelliJ Platform plugin that brings MAIFlow tasks into the JetBrains IDE through a `MAIFlow` tool window and IDE actions.

The plugin should use MAIFlow's documented MCP endpoint as its only application integration boundary:

```text
https://app.maiflow.org/api/mcp
```

Authentication must use the same user-created `mf_live_...` MAIFlow API token used by MCP. There is no separate plugin key. The plugin must store that token with the IntelliJ Platform Credentials Store (`PasswordSafe`), never in project files, logs, URLs, source code, or the plugin distribution. The plugin must not implement Keycloak login, read MAIFlow browser cookies, or access Totalum/PostgreSQL directly.

The first release should be a focused task-management client. It should not attempt to become a language plugin, code analyzer, or replacement for JetBrains AI Assistant. JetBrains AI Assistant can be configured separately to use MAIFlow as a remote MCP server; the plugin may provide setup guidance for that workflow.

## 2. Repository baseline

The actual starter is `maiflow-jetbrains-plugin`:

- Kotlin production sources under `src/main/kotlin`.
- `org.jetbrains.intellij.platform` version `2.18.1`.
- IntelliJ IDEA target `2025.3.6` (build branch `253`).
- Kotlin `2.3.20` and Gradle wrapper `9.6.1`.
- Platform-only dependency: `com.intellij.modules.platform`.
- Generated sample tool window: `MyToolWindowFactory`.
- Generated placeholder metadata in `src/main/resources/META-INF/plugin.xml`.

The generated sample code and placeholder metadata should be replaced before implementation. The current build is already on the recommended IntelliJ Platform Gradle Plugin 2.x path; do not migrate it to the obsolete Gradle IntelliJ Plugin 1.x.

## 3. Goals and non-goals

### Goals

1. Let a developer see their MAIFlow workspace without leaving the IDE.
2. Let a developer create a task from the IDE.
3. Let a developer update task status and score inputs from the IDE.
4. Let a developer open the full MAIFlow task or flow page in a browser.
5. Associate an IDE project with a MAIFlow flow and optional MAIFlow project.
6. Keep network work cancellable and off the Event Dispatch Thread.
7. Provide clear handling for authentication, paid-plan, permission, validation, and network failures.
8. Preserve a clean boundary so the MAIFlow API can evolve without coupling UI code to JSON-RPC details.

### Non-goals for v1

- Keycloak login, password handling, or session-cookie reuse.
- Direct access to MAIFlow's database or internal server modules.
- App Builder, Voiceflow, billing, admin, or token-management API operations.
- Code inspections, completion, refactoring, syntax highlighting, or language support.
- Uploading source code or selected editor text automatically.
- Continuous high-frequency polling.
- A hard dependency on JetBrains AI Assistant or the JetBrains HTTP Client plugin.

## 4. MAIFlow integration contract

MAIFlow exposes a stateless Streamable HTTP MCP endpoint. Its current server supports protocol versions `2025-06-18`, `2025-03-26`, and `2024-11-05`, and exposes two tools:

- `maiflow_capabilities` — returns the allowlisted MAIFlow routes and methods.
- `maiflow_api_request` — calls an authorized relative `/api/...` route while forwarding the same bearer token.

The plugin should implement a narrow typed client over those tools rather than expose arbitrary JSON-RPC calls throughout the UI.

### Request behavior

Every request must:

- `POST` one JSON-RPC request object to the configured MCP endpoint.
- Send `Authorization: Bearer <the same MAIFlow MCP token>`.
- Send `Content-Type: application/json` and `Accept: application/json`.
- Use a fresh request ID for each call.
- Use `initialize` before the first capability or API call after configuration changes.
- Treat the server as sessionless. Do not require or persist an MCP session ID.
- Enforce HTTPS for non-localhost endpoints; allow `http://localhost` and loopback URLs for development.

Initialization payload:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {},
    "clientInfo": {
      "name": "maiflow-jetbrains-plugin",
      "version": "<plugin-version>"
    }
  }
}
```

The client should call `maiflow_capabilities` during **Test Connection** and cache the returned route catalog for the current application session. It should still use typed route methods so a server-side allowlist change produces a controlled error instead of an unsafe fallback.

### v1 typed operations

| Plugin operation | MCP tool call | MAIFlow route/body |
|---|---|---|
| Load workspace | `maiflow_api_request` | `GET /api/workspace/tasks` |
| Load small chrome state, if notifications are added | `maiflow_api_request` | `GET /api/workspace/chrome` |
| Create task | `maiflow_api_request` | `POST /api/tasks` with `title`, `flowId`, optional `description`, `priority`, `urgency`, `impact`, `effortPoints`, `projectId` |
| Update task | `maiflow_api_request` | `PUT /api/tasks/:taskId` with any supported mutable fields |
| Delete task, if enabled later | `maiflow_api_request` | `DELETE /api/tasks/:taskId`, confirmation required |
| Create flow, if enabled later | `maiflow_api_request` | `POST /api/flows` |
| Create project, if enabled later | `maiflow_api_request` | `POST /api/projects` |

The workspace response contains `viewer`, `organizations`, `projects`, `flows`, `tasks`, and `tasksComplete`. The plugin should map only the fields it renders and tolerate additional server fields. Current task statuses are `todo`, `in_progress`, `blocked`, and `completed`; task scores are integers from `0` through `10`.

### Error mapping

The client must preserve the upstream status and user-facing message while keeping credentials out of diagnostics.

| Status | UI behavior |
|---|---|
| `401` | “MAIFlow token is invalid, revoked, or missing.” Offer Settings and Profile links. |
| `402` | “MAIFlow MCP access requires an active Personal or Team plan.” Offer Billing/Profile link. |
| `403` | Explain that the account, verification, organization membership, role, or entitlement prevents the operation. |
| `400` | Show validation feedback and do not retry. |
| `404` | Refresh once; if still missing, report that the task/flow no longer exists or is inaccessible. |
| `429`, `5xx`, timeout, connection failure | Offer retry. Retry reads/capabilities only; never blindly retry a write. |
| Malformed JSON-RPC or unexpected response | Report a generic protocol error and include a redacted diagnostic entry in the IDE log. |

## 5. User experience

### Configuration

Add `Settings | Tools | MAIFlow` with:

- Server URL, defaulting to `https://app.maiflow.org/api/mcp`.
- API token password field for the same token created in MAIFlow Profile → API access and used by MCP.
- **Test Connection** action.
- **Open MAIFlow Profile** action, opening the browser to the configured app origin's Profile page.
- A redacted token prefix/status and last successful connection time.
- A short warning that rotating or revoking a MAIFlow token invalidates existing clients, including this plugin and MCP clients.

Persist the server URL and non-secret metadata using an application-level persistent settings service. Persist the token only through `PasswordSafe`, keyed by a normalized endpoint identity. Never serialize the plaintext token in `PersistentStateComponent` state. Do not attempt to read or mutate JetBrains AI Assistant's private MCP configuration; both integrations use the same MAIFlow credential independently.

### MAIFlow tool window

Use the short, title-case name `MAIFlow`. The window should be a vertical tree/list-oriented tool window with a concise toolbar:

- Refresh.
- Flow/project filter.
- Create task.
- Open Settings.
- Open current selection in MAIFlow.

Suggested content layout:

```text
MAIFlow
├── Filter / Refresh / Create
├── Current task or selected flow
└── Task list
    ├── status chip · title · flow/project · score
    └── assignee · priority · updated time
```

Each task opens a detail view or dialog containing title, description, status, priority, urgency, impact, effort, assignee, flow, project, and engine score. Mutations must be explicit and show a success/error result; the list refreshes after a successful write.

Required states:

- Not configured: empty state with **Configure MAIFlow**.
- Loading: progress indicator and disabled duplicate actions.
- Loaded: task list and filters.
- Empty: explain that no reachable tasks were returned.
- Stale: show cached data with a visible refresh/error affordance.
- Unauthorized/paid-plan/permission failure: actionable message with the appropriate settings/profile/billing action.
- Offline or timeout: cached data remains visible when available.

The tool window should not be shown by default for an unconfigured project unless the user explicitly opens it from a MAIFlow action. Use the tool-window applicability mechanism to avoid presenting irrelevant UI in every project.

### Project mapping

Add a project-level mapping in `Settings | Tools | MAIFlow` or from the tool window:

- MAIFlow flow ID, required for creating tasks.
- Optional MAIFlow project ID.
- Optional “use this mapping for new tasks” flag.

Store mapping IDs in project state, preferably user-specific workspace state. Store no API token or token-derived data in project files. If a future team workflow needs VCS sharing, add an explicit opt-in mapping file with only non-secret IDs.

### IDE context safety

The default create-task flow may use IDE metadata such as project name, repository name, branch name, and current file path. It must not send source code, selected text, whole-file contents, or diagnostics unless the user explicitly opts in for that particular request. Any opt-in code context must be visibly summarized before submission.

## 6. Technical architecture

Use Kotlin for new implementation. Kotlin is already configured in the starter and is the better fit for the IntelliJ UI DSL, structured cancellation, and platform coroutine scopes. Use standard Swing/IntelliJ components for the interactive tool window; Kotlin UI DSL is intended primarily for settings/forms rather than general tool-window controls.

Recommended package structure:

```text
tech.conceptualarts.maiflow
├── actions
│   ├── ConfigureMAIFlowAction
│   ├── CreateTaskAction
│   ├── RefreshTasksAction
│   └── OpenTaskAction
├── api
│   ├── McpHttpTransport
│   ├── McpClient
│   ├── McpModels
│   ├── MAIFlowApi
│   └── MAIFlowApiError
├── model
│   ├── WorkspaceSnapshot
│   ├── TaskSummary
│   ├── FlowSummary
│   └── ProjectSummary
├── services
│   ├── MAIFlowProjectService
│   ├── MAIFlowApplicationSettings
│   └── MAIFlowTokenStore
├── settings
│   └── MAIFlowConfigurable
└── toolwindow
    ├── MAIFlowToolWindowFactory
    ├── MAIFlowPanel
    └── TaskDetailPanel
```

Responsibilities:

- `McpHttpTransport`: endpoint validation, headers, timeouts, JSON-RPC envelope, response decoding, redacted logging.
- `McpClient`: initialize, call tool, normalize JSON-RPC errors.
- `MAIFlowApi`: typed workspace/task/flow/project operations and route-specific payloads.
- `MAIFlowProjectService`: project-scoped cache, refresh state, mutations, and UI-facing observable state.
- `MAIFlowApplicationSettings`: server URL and non-secret connection preferences.
- `MAIFlowTokenStore`: `PasswordSafe` access only; no token getter exposed to UI logging or model serialization.
- UI classes: render state and dispatch actions; no raw HTTP or JSON parsing.

Use a project service with an injected coroutine scope for project-lifetime work. Network requests must run on a background dispatcher and be cancellable when the project closes or the plugin unloads. UI updates must be marshalled back to the UI thread. Actions must implement an explicit `getActionUpdateThread()` and keep `update()` fast.

For HTTP, use a public JDK/platform-supported client and the IDE's proxy/certificate settings where the selected baseline exposes that API. Do not depend on bundled OkHttp classes or on JetBrains AI Assistant internals. Pin any third-party JSON dependency explicitly if the selected target does not provide a stable compatible JSON API.

Cache policy:

- In-memory cache for the current session and project.
- No persistence of task descriptions, comments, attachments, or token-bearing responses by default.
- Refresh on tool-window activation, manual refresh, successful mutation, and an optional conservative interval only while the window is visible.
- Cancel and replace an in-flight refresh rather than allowing overlapping reads.

## 7. Plugin metadata and Gradle changes

Before the first release:

1. Change the stable plugin ID from `tech.conceptualarts.maiflow-jetbrains-plugin` to `tech.conceptualarts.maiflow` before publishing. Once published, do not change it.
2. Change the display name to `MAIFlow`.
3. Replace the placeholder vendor and description with Conceptual Arts/MAIFlow information and a support URL.
4. Keep `com.intellij.modules.platform` as the only required platform dependency for v1.
5. Keep target IntelliJ IDEA `2025.3.5` and set effective compatibility to build `253` or later. Omit `until-build` unless a verified compatibility break requires a bounded release.
6. Set Java toolchain/source compatibility to Java 21 for the 2025.3 target.
7. Keep Kotlin standard-library bundling disabled, as already configured.
8. Replace the sample `MyToolWindow` registration and classes with MAIFlow registrations.
9. Add a dedicated monochrome 16×16 and 20×20 tool-window icon; the existing colored plugin logo can remain the Marketplace/plugin icon.
10. Do not add `com.intellij.tasks` until the optional native Tasks & Contexts integration is implemented and tested as a separately guarded feature.

Expected platform registrations for v1:

- Tool window factory.
- Application settings service/configurable.
- Project service.
- Configure/create/refresh/open-task actions.
- Optional post-startup refresh only when configuration exists; do not perform a blocking network call during IDE startup.

Use modern services and persistent state. Do not use legacy application/project components.

## 8. Optional native Tasks & Contexts integration

After the tool-window vertical slice is stable, evaluate implementing MAIFlow as an IntelliJ Task Management connector:

- `TaskRepositoryType` for MAIFlow server configuration.
- `TaskRepository` for loading/searching MAIFlow tasks and updating supported states.
- `Task`/custom task states mapped from MAIFlow's four statuses.
- Credentials Store for the same API token.
- Optional dependency on the Task Management plugin, with graceful behavior when it is not installed.

This would allow MAIFlow tasks to participate in the IDE's task switcher, changelists, editor tabs, and context saving. It should remain a later phase because it introduces another JetBrains plugin dependency and a second configuration surface. The MAIFlow tool window remains the primary UX until the connector has proven useful.

## 9. JetBrains AI Assistant relationship

JetBrains AI Assistant supports remote Streamable HTTP MCP servers. A user can configure MAIFlow independently with a JSON shape such as:

```json
{
  "mcpServers": {
    "maiflow": {
      "url": "https://app.maiflow.org/api/mcp",
      "headers": {
        "Authorization": "Bearer mf_live_<the-same-token>"
      }
    }
  }
}
```

The plugin should provide a documentation link and a redacted copyable template, but should not require AI Assistant to be installed. Do not assume a public API exists for another plugin's MCP settings page; validate any “configure automatically” idea against the exact target IDE before implementing it. A future release may add a dedicated action that opens the relevant settings page if a stable public action ID is available.

## 10. Testing and acceptance

### Automated tests

- JSON-RPC request/response serialization and protocol-version negotiation.
- MCP tool-call extraction, including `structuredContent` and `isError` responses.
- Endpoint normalization and localhost/HTTPS validation.
- Error/status mapping and message redaction.
- Token-store behavior using a test service; assert plaintext tokens never enter persistent state or logs.
- Workspace/task mapping with missing and additional fields.
- Create/update payload validation and status mapping.
- Settings persistence and project mapping persistence.
- Project service cancellation and refresh coalescing.
- Tool-window state rendering in a headless IntelliJ Platform test.

### Opt-in integration tests

Support environment variables such as `MAIFLOW_PLUGIN_E2E_URL` and `MAIFLOW_PLUGIN_E2E_TOKEN`, never committed to the repository. Read-only tests should cover `initialize`, `maiflow_capabilities`, and workspace loading. Write tests must be disabled unless explicitly enabled and should use a disposable test account/flow.

### Manual acceptance matrix

1. Fresh install with no configuration shows a useful setup state.
2. Valid token and paid account load flows, projects, and tasks.
3. Invalid/revoked token shows actionable `401` guidance without exposing the secret.
4. Free/lapsed account shows actionable `402` guidance.
5. Inaccessible organization/task shows `403`/`404` guidance.
6. Create task succeeds and appears after refresh.
7. Update status succeeds and preserves other fields.
8. Timeout/offline mode keeps cached data visible and offers retry.
9. IDE close/plugin unload cancels network work cleanly.
10. The plugin runs in IntelliJ IDEA and at least one non-Java JetBrains IDE without a language-specific dependency.

### Required Gradle checks

```text
./gradlew check
./gradlew verifyPlugin
./gradlew buildPlugin
```

Also exercise the generated `Run IDE with Plugin` configuration. Run Plugin Verifier against every declared target product/version before publishing.

## 11. Delivery phases

### Phase 0 — Replace the scaffold

- Rename metadata and packages.
- Remove the random-number sample.
- Add settings, token store, service skeleton, and test fixtures.
- Confirm `runIde`, `check`, and `verifyPlugin` work.

### Phase 1 — Vertical slice

- Configure endpoint/token.
- Test connection through MCP initialization and capabilities.
- Load and render `/api/workspace/tasks`.
- Handle all documented error classes.

### Phase 2 — Task workflow

- Create task.
- Update task status and score inputs.
- Add task detail panel, browser links, filters, cache, and refresh coalescing.

### Phase 3 — IDE workflow integration

- Project-to-flow/project mapping.
- Safe IDE metadata context.
- Evaluate native Tasks & Contexts connector.
- Add AI Assistant setup guidance.

### Phase 4 — Release hardening

- Marketplace description, support link, screenshots, and changelog.
- Plugin signing and publishing secrets only in CI/local environment configuration.
- Plugin Verifier matrix and compatibility review.
- Opt-in production telemetry only if a privacy policy and user consent model are agreed; otherwise rely on redacted local logs.

## 12. Risks and decisions to confirm during implementation

- MAIFlow currently has one active API token per user; token rotation invalidates the old token. The UI must make that consequence clear for this plugin and other MCP clients using the same token.
- The MCP endpoint is the documented boundary, but its current server is intentionally sessionless and returns JSON responses. Keep transport details isolated so a future Streamable HTTP session/streaming change affects one class.
- Workspace loading is intentionally broad. Add pagination or a dedicated lightweight read route if large workspaces make the tool window slow.
- The full MAIFlow web app uses paths such as `/tasks/:taskId`, but browser deep-link construction should be confirmed against the deployed app before release.
- Native Task Management integration may be valuable but should not delay the first usable tool-window release.

## 13. References

Repository references:

- [MAIFlow MCP documentation](../MAIFlow/project-docs/mcp.md)
- [MAIFlow MCP route implementation](../MAIFlow/src/app/api/mcp/route.ts)
- [MAIFlow task create route](../MAIFlow/src/app/api/tasks/route.ts)
- [MAIFlow task update route](../MAIFlow/src/app/api/tasks/[taskId]/route.ts)
- [Current JetBrains starter build](./build.gradle.kts)
- [Current plugin descriptor](./src/main/resources/META-INF/plugin.xml)

Official JetBrains references:

- [Introduction to Plugin Development](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)
- [IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Creating a Plugin Project](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html)
- [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html)
- [Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)
- [Tool Windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
- [Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)
- [Settings Guide](https://plugins.jetbrains.com/docs/intellij/settings-guide.html)
- [Persisting Sensitive Data](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html)
- [Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
- [Background Processes](https://plugins.jetbrains.com/docs/intellij/background-processes.html)
- [Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)
- [Kotlin UI DSL](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html)
- [Verifying Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)
- [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
- [JetBrains AI Assistant MCP documentation](https://www.jetbrains.com/help/ai-assistant/mcp.html)
- [JetBrains IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
