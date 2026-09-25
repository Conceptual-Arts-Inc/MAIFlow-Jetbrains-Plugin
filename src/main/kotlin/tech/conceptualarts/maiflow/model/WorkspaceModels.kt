package tech.conceptualarts.maiflow.model

enum class TaskStatus(val wireValue: String, val displayName: String) {
    TODO("todo", "To do"),
    IN_PROGRESS("in_progress", "In progress"),
    BLOCKED("blocked", "Blocked"),
    COMPLETED("completed", "Completed");

    companion object {
        fun fromWire(value: String?): TaskStatus = entries.firstOrNull { it.wireValue == value } ?: TODO
    }
}

data class MemberSummary(
    val id: String,
    val name: String,
    val initials: String,
    val role: String = "Member",
    val imageUrl: String? = null,
)

data class OrganizationSummary(
    val id: String,
    val name: String,
    val viewerRole: String = "Member",
)

data class FlowSummary(
    val id: String,
    val name: String,
    val scope: String = "personal",
    val organizationId: String? = null,
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val organizationId: String? = null,
)

data class TaskSummary(
    val id: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val flowId: String,
    val flowName: String,
    val projectId: String? = null,
    val projectName: String? = null,
    val assignee: MemberSummary? = null,
    val priority: Int = 5,
    val urgency: Int = 5,
    val impact: Int = 5,
    val effortPoints: Int = 3,
    val engineScore: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class WorkspaceSnapshot(
    val viewer: MemberSummary? = null,
    val organizations: List<OrganizationSummary> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val flows: List<FlowSummary> = emptyList(),
    val tasks: List<TaskSummary> = emptyList(),
    val tasksComplete: Boolean = true,
) {
    /** Keeps the IDE view stable even when the API returns collections in an arbitrary order. */
    fun sortedForDisplay(): WorkspaceSnapshot = copy(
        organizations = organizations.sortedWith(compareBy<OrganizationSummary> { it.name.lowercase() }.thenBy { it.id }),
        projects = projects.sortedWith(compareBy<ProjectSummary> { it.name.lowercase() }.thenBy { it.id }),
        flows = flows.sortedWith(compareBy<FlowSummary> { it.name.lowercase() }.thenBy { it.id }),
        tasks = tasks.sortedWith(
            compareBy<TaskSummary> { it.status.displayOrder }
                .thenByDescending { it.updatedAt.ifBlank { it.createdAt } }
                .thenByDescending { it.engineScore }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id },
        ),
    )
}

private val TaskStatus.displayOrder: Int
    get() = when (this) {
        TaskStatus.IN_PROGRESS -> 0
        TaskStatus.BLOCKED -> 1
        TaskStatus.TODO -> 2
        TaskStatus.COMPLETED -> 3
    }

data class CreateTaskRequest(
    val title: String,
    val flowId: String,
    val description: String = "",
    val priority: Int = 5,
    val urgency: Int? = null,
    val impact: Int? = null,
    val effortPoints: Int? = null,
    val projectId: String? = null,
)

data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val status: TaskStatus? = null,
    val priority: Int? = null,
    val urgency: Int? = null,
    val impact: Int? = null,
    val effortPoints: Int? = null,
    val assigneeId: String? = null,
    val projectId: String? = null,
    val detachProject: Boolean = false,
) {
    fun isEmpty(): Boolean = title == null && description == null && status == null && priority == null &&
        urgency == null && impact == null && effortPoints == null && assigneeId == null && projectId == null && !detachProject
}

sealed class ProjectViewState {
    data object NotConfigured : ProjectViewState()
    data class Loading(val cached: WorkspaceSnapshot? = null) : ProjectViewState()
    data class Loaded(val snapshot: WorkspaceSnapshot) : ProjectViewState()
    data class Empty(val snapshot: WorkspaceSnapshot) : ProjectViewState()
    data class Stale(val snapshot: WorkspaceSnapshot, val error: String) : ProjectViewState()
    data class Failed(val error: String, val cached: WorkspaceSnapshot? = null) : ProjectViewState()
}
