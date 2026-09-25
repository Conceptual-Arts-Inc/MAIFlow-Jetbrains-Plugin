package tech.conceptualarts.maiflow.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.components.Service
import tech.conceptualarts.maiflow.api.MAIFlowApi
import tech.conceptualarts.maiflow.api.MAIFlowApiError
import tech.conceptualarts.maiflow.model.CreateTaskRequest
import tech.conceptualarts.maiflow.model.ProjectViewState
import tech.conceptualarts.maiflow.model.TaskSummary
import tech.conceptualarts.maiflow.model.UpdateTaskRequest
import tech.conceptualarts.maiflow.model.WorkspaceSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.swing.SwingUtilities

data class MAIFlowProjectMappingState(
    var flowId: String = "",
    var projectId: String = "",
    var useForNewTasks: Boolean = false,
)

@State(name = "MAIFlowProjectMapping", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class MAIFlowProjectService(private val project: Project) : PersistentStateComponent<MAIFlowProjectMappingState>, Disposable {
    private val api = MAIFlowApi.getInstance()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MAIFlow-${project.name}").apply { isDaemon = true }
    }
    private val listeners = CopyOnWriteArrayList<(ProjectViewState) -> Unit>()
    private val busyListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private var mapping = MAIFlowProjectMappingState()
    private var currentState: ProjectViewState = ProjectViewState.NotConfigured
    private var currentSnapshot: WorkspaceSnapshot? = null
    private var inFlight: Future<*>? = null
    private var operationGeneration = 0L
    @Volatile private var busy = false
    @Volatile private var selectedTask: TaskSummary? = null

    override fun getState(): MAIFlowProjectMappingState = mapping

    override fun loadState(state: MAIFlowProjectMappingState) {
        XmlSerializerUtil.copyBean(state, mapping)
    }

    fun addListener(listener: (ProjectViewState) -> Unit) {
        listeners += listener
        publishTo(listener, currentState)
    }

    fun removeListener(listener: (ProjectViewState) -> Unit) {
        listeners -= listener
    }

    fun addBusyListener(listener: (Boolean) -> Unit) {
        busyListeners += listener
        publishBusyTo(listener, busy)
    }

    fun removeBusyListener(listener: (Boolean) -> Unit) {
        busyListeners -= listener
    }

    fun state(): ProjectViewState = currentState
    fun snapshot(): WorkspaceSnapshot? = currentSnapshot
    fun mapping(): MAIFlowProjectMappingState = mapping.copy()
    fun isBusy(): Boolean = busy
    fun selectedTask(): TaskSummary? = selectedTask
    fun setSelectedTask(task: TaskSummary?) { selectedTask = task }

    fun setMapping(flowId: String, projectId: String, useForNewTasks: Boolean) {
        mapping.flowId = flowId.trim()
        mapping.projectId = projectId.trim()
        mapping.useForNewTasks = useForNewTasks
    }

    fun refresh() {
        if (project.isDisposed) return
        if (!MAIFlowApplicationSettings.getInstance().isConfigured()) {
            currentSnapshot = null
            publish(ProjectViewState.NotConfigured)
            return
        }
        val cached = currentSnapshot
        publish(ProjectViewState.Loading(cached))
        val generation = synchronized(this) {
            inFlight?.cancel(true)
            operationGeneration += 1
            operationGeneration
        }
        synchronized(this) {
            setBusy(true)
            inFlight = executor.submit {
                try {
                    val loaded = api.loadWorkspace()
                    val ordered = loaded.sortedForDisplay()
                    currentSnapshot = ordered
                    publish(if (ordered.tasks.isEmpty()) ProjectViewState.Empty(ordered) else ProjectViewState.Loaded(ordered))
                } catch (error: Throwable) {
                    if (error is InterruptedException || Thread.currentThread().isInterrupted) return@submit
                    val message = (error as? MAIFlowApiError)?.message ?: "Could not load MAIFlow tasks."
                    val snapshot = currentSnapshot
                    publish(if (snapshot != null) ProjectViewState.Stale(snapshot, message) else ProjectViewState.Failed(message))
                } finally {
                    finishOperation(generation)
                }
            }
        }
    }

    fun createTask(request: CreateTaskRequest, onComplete: (Result<String>) -> Unit = {}) {
        runMutation({ api.createTask(request) }, onComplete)
    }

    fun updateTask(taskId: String, request: UpdateTaskRequest, onComplete: (Result<Unit>) -> Unit = {}) {
        runMutation({ api.updateTask(taskId, request) }, onComplete)
    }

    private fun <T> runMutation(work: () -> T, onComplete: (Result<T>) -> Unit) {
        if (project.isDisposed) return
        val generation = synchronized(this) {
            operationGeneration += 1
            operationGeneration
        }
        setBusy(true)
        executor.submit {
            val result = runCatching { work() }
            finishOperation(generation)
            SwingUtilities.invokeLater {
                if (project.isDisposed) return@invokeLater
                onComplete(result)
                if (result.isSuccess) refresh()
            }
        }
    }

    private fun finishOperation(generation: Long) {
        synchronized(this) {
            if (generation == operationGeneration) setBusy(false)
        }
    }

    private fun publish(state: ProjectViewState) {
        currentState = state
        if (SwingUtilities.isEventDispatchThread()) {
            listeners.forEach { it(state) }
        } else {
            SwingUtilities.invokeLater {
                if (!project.isDisposed) listeners.forEach { it(state) }
            }
        }
    }

    private fun publishTo(listener: (ProjectViewState) -> Unit, state: ProjectViewState) {
        if (SwingUtilities.isEventDispatchThread()) listener(state)
        else SwingUtilities.invokeLater { if (!project.isDisposed) listener(state) }
    }

    private fun publishBusyTo(listener: (Boolean) -> Unit, value: Boolean) {
        if (SwingUtilities.isEventDispatchThread()) listener(value)
        else SwingUtilities.invokeLater { if (!project.isDisposed) listener(value) }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        if (SwingUtilities.isEventDispatchThread()) busyListeners.forEach { it(value) }
        else SwingUtilities.invokeLater { if (!project.isDisposed) busyListeners.forEach { it(value) } }
    }

    override fun dispose() {
        synchronized(this) { inFlight?.cancel(true); inFlight = null }
        executor.shutdownNow()
        listeners.clear()
        busyListeners.clear()
    }

    companion object {
        fun getInstance(project: Project): MAIFlowProjectService = project.getService(MAIFlowProjectService::class.java)
    }
}
