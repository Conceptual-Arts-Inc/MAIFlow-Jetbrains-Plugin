package tech.conceptualarts.maiflow.api

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import tech.conceptualarts.maiflow.model.CreateTaskRequest
import tech.conceptualarts.maiflow.model.FlowSummary
import tech.conceptualarts.maiflow.model.MemberSummary
import tech.conceptualarts.maiflow.model.OrganizationSummary
import tech.conceptualarts.maiflow.model.ProjectSummary
import tech.conceptualarts.maiflow.model.TaskStatus
import tech.conceptualarts.maiflow.model.TaskSummary
import tech.conceptualarts.maiflow.model.UpdateTaskRequest
import tech.conceptualarts.maiflow.model.WorkspaceSnapshot
import tech.conceptualarts.maiflow.services.MAIFlowApplicationSettings
import tech.conceptualarts.maiflow.services.MAIFlowTokenStore

data class ConnectionTestResult(val initialization: McpInitialization, val catalog: McpCapabilityCatalog)

@Service(Service.Level.APP)
class MAIFlowApi {
    private val lock = Any()
    private var activeSession: Session? = null

    fun loadWorkspace(): WorkspaceSnapshot {
        return try {
            callWorkspace()
        } catch (error: MAIFlowApiError) {
            if (error.kind != MAIFlowErrorKind.NOT_FOUND) throw error
            // A stale route/catalog or workspace record can briefly produce 404. Refresh once,
            // then surface the original class of error instead of retrying indefinitely.
            synchronized(lock) { activeSession?.catalog = null }
            callWorkspace()
        }
    }

    fun createTask(request: CreateTaskRequest): String {
        validateCreate(request)
        val body = JsonObject().apply {
            addProperty("title", request.title.trim())
            addProperty("flowId", request.flowId.trim())
            if (request.description.isNotBlank()) addProperty("description", request.description)
            addProperty("priority", request.priority)
            request.urgency?.let { addProperty("urgency", it) }
            request.impact?.let { addProperty("impact", it) }
            request.effortPoints?.let { addProperty("effortPoints", it) }
            request.projectId?.takeIf { it.isNotBlank() }?.let { addProperty("projectId", it) }
        }
        val response = callRoute("POST", "/api/tasks", body)
        return response.objectValue("data")?.string("id")
            ?: throw MAIFlowApiError.protocol("Create task response omitted its id")
    }

    fun updateTask(taskId: String, request: UpdateTaskRequest) {
        require(taskId.isNotBlank()) { "Task id cannot be empty." }
        validateUpdate(request)
        val body = JsonObject().apply {
            request.title?.let { addProperty("title", it) }
            request.description?.let { addProperty("description", it) }
            request.status?.let { addProperty("status", it.wireValue) }
            request.priority?.let { addProperty("priority", it) }
            request.urgency?.let { addProperty("urgency", it) }
            request.impact?.let { addProperty("impact", it) }
            request.effortPoints?.let { addProperty("effortPoints", it) }
            request.assigneeId?.let { addProperty("assigneeId", it) }
            when {
                request.detachProject -> add("projectId", JsonNull.INSTANCE)
                request.projectId != null -> addProperty("projectId", request.projectId)
            }
        }
        callRoute("PUT", "/api/tasks/${encodePathSegment(taskId)}", body)
    }

    /** Runs initialize and capabilities against the supplied unsaved settings. */
    fun testConnection(endpoint: String, token: String): ConnectionTestResult {
        val normalized = try {
            EndpointIdentity.normalize(endpoint)
        } catch (error: IllegalArgumentException) {
            throw MAIFlowApiError.configuration(error.message ?: "MAIFlow server URL is invalid.")
        }
        requireToken(token)
        val client = McpClient()
        val initialization = client.initialize(normalized, token, PLUGIN_VERSION)
        val catalog = client.capabilities(normalized, token)
        synchronized(lock) {
            activeSession = Session(normalized, token, client, initialized = true, catalog = catalog)
        }
        return ConnectionTestResult(initialization, catalog)
    }

    fun clearSession() {
        synchronized(lock) { activeSession = null }
    }

    private fun callWorkspace(): WorkspaceSnapshot {
        val response = callRoute("GET", "/api/workspace/tasks")
        return WorkspaceMapper.map(dataObject(response))
    }

    private fun callRoute(method: String, path: String, body: JsonObject? = null): JsonObject {
        val session = session()
        session.ensureInitialized()
        val catalog = session.catalog ?: session.refreshCatalog()
        if (!catalog.allows(method, path)) {
            throw MAIFlowApiError.protocol("The configured MAIFlow server does not advertise $method $path")
        }
        val arguments = JsonObject().apply {
            addProperty("method", method)
            addProperty("path", path)
            body?.let { add("body", it) }
        }
        val result = session.client.callTool(session.endpoint, session.token, "maiflow_api_request", arguments)
        val structured = result.structuredContent
        val status = structured?.int("status")
        val ok = structured?.bool("ok") ?: !result.isError
        val response = structured?.get("response")?.asObjectOrNull()
            ?: result.textContent?.let { parseTextResponse(it) }
        if (!ok || (status != null && status !in 200..299)) {
            throw MAIFlowApiError.fromStatus(status ?: 500, response?.string("error") ?: response?.string("message"))
        }
        return response ?: JsonObject()
    }

    private fun session(): Session {
        val settings = MAIFlowApplicationSettings.getInstance()
        val endpoint = try {
            EndpointIdentity.normalize(settings.serverUrl)
        } catch (error: IllegalArgumentException) {
            throw MAIFlowApiError.configuration(error.message ?: "MAIFlow server URL is invalid.")
        }
        val token = requireToken(MAIFlowTokenStore.getInstance().readToken(endpoint))
        synchronized(lock) {
            val current = activeSession
            if (current != null && current.endpoint == endpoint && current.token == token) return current
            val created = Session(endpoint, token, McpClient())
            activeSession = created
            return created
        }
    }

    private fun parseTextResponse(text: String): JsonObject = try {
        com.google.gson.JsonParser.parseString(text).asObjectOrNull() ?: JsonObject()
    } catch (error: Exception) {
        throw MAIFlowApiError.protocol("The MAIFlow tool response was not valid JSON", error)
    }

    private fun dataObject(response: JsonObject): JsonObject = response.objectValue("data") ?: response

    private fun validateCreate(request: CreateTaskRequest) {
        if (request.title.trim().isEmpty() || request.title.trim().length > 160) invalid("Task title must be between 1 and 160 characters.")
        if (request.flowId.trim().isEmpty()) invalid("A MAIFlow flow is required to create a task.")
        validateScore("priority", request.priority)
        request.urgency?.let { validateScore("urgency", it) }
        request.impact?.let { validateScore("impact", it) }
        request.effortPoints?.let { validateScore("effort", it) }
        if (request.description.length > 4000) invalid("Task description must be 4,000 characters or fewer.")
    }

    private fun validateUpdate(request: UpdateTaskRequest) {
        if (request.isEmpty()) invalid("Choose at least one task field to update.")
        request.title?.let { if (it.trim().isEmpty() || it.trim().length > 160) invalid("Task title must be between 1 and 160 characters.") }
        request.description?.let { if (it.length > 4000) invalid("Task description must be 4,000 characters or fewer.") }
        request.priority?.let { validateScore("priority", it) }
        request.urgency?.let { validateScore("urgency", it) }
        request.impact?.let { validateScore("impact", it) }
        request.effortPoints?.let { validateScore("effort", it) }
    }

    private fun validateScore(name: String, value: Int) {
        if (value !in 0..10) invalid("$name must be between 0 and 10.")
    }

    private fun invalid(message: String): Nothing = throw MAIFlowApiError(null, message, MAIFlowErrorKind.VALIDATION)

    private fun requireToken(token: String?): String {
        if (token.isNullOrBlank()) throw MAIFlowApiError.configuration("Add your MAIFlow API token in Settings | Tools | MAIFlow.")
        if (!token.startsWith("mf_live_")) throw MAIFlowApiError.configuration("MAIFlow API tokens must start with mf_live_.")
        return token
    }

    private fun encodePathSegment(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private class Session(
        val endpoint: String,
        val token: String,
        val client: McpClient,
        var initialized: Boolean = false,
        var catalog: McpCapabilityCatalog? = null,
    ) {
        fun ensureInitialized() {
            if (!initialized) {
                client.initialize(endpoint, token, PLUGIN_VERSION)
                initialized = true
            }
        }

        fun refreshCatalog(): McpCapabilityCatalog {
            val loaded = client.capabilities(endpoint, token)
            catalog = loaded
            return loaded
        }
    }

    companion object {
        private const val PLUGIN_VERSION = "1.0.0"

        fun getInstance(): MAIFlowApi =
            com.intellij.openapi.application.ApplicationManager.getApplication().getService(MAIFlowApi::class.java)
    }
}

internal object WorkspaceMapper {
    fun map(root: JsonObject): WorkspaceSnapshot {
        val organizations = root.arrayValue("organizations")?.mapNotNull { it.asObjectOrNull()?.toOrganization() }.orEmpty()
        val projects = root.arrayValue("projects")?.mapNotNull { it.asObjectOrNull()?.toProject() }.orEmpty()
        val flows = root.arrayValue("flows")?.mapNotNull { it.asObjectOrNull()?.toFlow() }.orEmpty()
        val viewer = root.objectValue("viewer")?.toMember()
        val flowById = flows.associateBy { it.id }
        val projectById = projects.associateBy { it.id }
        val tasks = root.arrayValue("tasks")?.mapNotNull { element ->
            val task = element.asObjectOrNull() ?: return@mapNotNull null
            task.toTask(flowById, projectById, viewer)
        }.orEmpty()
        return WorkspaceSnapshot(
            viewer = viewer,
            organizations = organizations,
            projects = projects,
            flows = flows,
            tasks = tasks,
            tasksComplete = root.bool("tasksComplete") ?: true,
        )
    }

    private fun JsonObject.toMember(): MemberSummary? {
        val id = ref(this, "id") ?: ref(this, "_id") ?: return null
        val name = string("name") ?: string("displayName") ?: string("email") ?: "MAIFlow member"
        return MemberSummary(id, name, string("initials") ?: initials(name), string("role") ?: "Member", string("imageUrl"))
    }

    private fun JsonObject.toOrganization(): OrganizationSummary? {
        val id = ref(this, "id") ?: ref(this, "_id") ?: return null
        return OrganizationSummary(id, string("name") ?: "Untitled organization", string("viewerRole") ?: "Member")
    }

    private fun JsonObject.toProject(): ProjectSummary? {
        val id = ref(this, "id") ?: ref(this, "_id") ?: return null
        return ProjectSummary(id, string("name") ?: "Untitled project", string("description").orEmpty(), ref(this, "orgId") ?: ref(this, "organizationId"))
    }

    private fun JsonObject.toFlow(): FlowSummary? {
        val id = ref(this, "id") ?: ref(this, "_id") ?: return null
        return FlowSummary(id, string("name") ?: "Untitled flow", string("scope") ?: "personal", ref(this, "orgId") ?: ref(this, "organizationId"))
    }

    private fun JsonObject.toTask(
        flows: Map<String, FlowSummary>,
        projects: Map<String, ProjectSummary>,
        viewer: MemberSummary?,
    ): TaskSummary? {
        val id = ref(this, "id") ?: ref(this, "_id") ?: return null
        val flowId = ref(this, "flowId") ?: ref(this, "flow") ?: "unknown-flow"
        val flow = flows[flowId]
        val projectId = ref(this, "projectId") ?: ref(this, "project")
        val project = projectId?.let { projects[it] }
        val assignee = objectValue("assignee")?.toMember() ?: viewer
        val created = string("createdAt") ?: string("created_at").orEmpty()
        return TaskSummary(
            id = id,
            title = string("title") ?: "Untitled task",
            description = string("description").orEmpty(),
            status = TaskStatus.fromWire(string("status")),
            flowId = flowId,
            flowName = string("flowName") ?: flow?.name ?: "Unknown flow",
            projectId = projectId,
            projectName = string("projectName") ?: project?.name,
            assignee = assignee,
            priority = score(int("priority")),
            urgency = score(int("urgency") ?: int("priority")),
            impact = score(int("impact") ?: int("priority")),
            effortPoints = score(int("effortPoints") ?: int("effort") ?: 3),
            engineScore = (int("engineScore") ?: int("score_engine_score") ?: 0).coerceAtLeast(0),
            createdAt = created,
            updatedAt = string("updatedAt") ?: string("updated_at") ?: created,
        )
    }

    private fun ref(parent: JsonObject, name: String): String? =
        parent.string(name) ?: parent.objectValue(name)?.let { ref(it, "id") ?: ref(it, "_id") }

    private fun score(value: Int?): Int = (value ?: 0).coerceIn(0, 10)

    private fun initials(name: String): String = name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "M" }
}
