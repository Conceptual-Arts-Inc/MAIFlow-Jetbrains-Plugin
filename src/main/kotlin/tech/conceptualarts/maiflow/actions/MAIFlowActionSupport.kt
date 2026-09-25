package tech.conceptualarts.maiflow.actions

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import tech.conceptualarts.maiflow.api.EndpointIdentity
import tech.conceptualarts.maiflow.api.MAIFlowApiError
import tech.conceptualarts.maiflow.model.CreateTaskRequest
import tech.conceptualarts.maiflow.services.MAIFlowApplicationSettings
import tech.conceptualarts.maiflow.services.MAIFlowProjectService
import tech.conceptualarts.maiflow.toolwindow.CreateTaskDialog

internal fun openSettings(project: Project) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, "MAIFlow")
}

internal fun showToolWindow(project: Project) {
    ToolWindowManager.getInstance(project).getToolWindow("MAIFlow")?.show()
        ?: openSettings(project)
}

internal fun createTaskFromAction(project: Project) {
    val service = MAIFlowProjectService.getInstance(project)
    val snapshot = service.snapshot()
    if (snapshot == null || snapshot.flows.isEmpty()) {
        service.refresh()
        Messages.showInfoMessage(project, "Refresh MAIFlow to load at least one reachable flow before creating a task.", "MAIFlow")
        return
    }
    val mapping = service.mapping()
    val dialog = CreateTaskDialog(snapshot, mapping.flowId, mapping.projectId)
    if (!dialog.showAndGet()) return
    service.createTask(dialog.request()) { result ->
        result.onFailure { showActionError(project, it) }
    }
}

internal fun showActionError(project: Project, error: Throwable) {
    val apiError = error as? MAIFlowApiError
    val message = apiError?.message ?: error.message ?: "MAIFlow request failed."
    val suffix = when (apiError?.kind) {
        tech.conceptualarts.maiflow.api.MAIFlowErrorKind.AUTHENTICATION -> " Open Settings to replace the token."
        tech.conceptualarts.maiflow.api.MAIFlowErrorKind.PAID_PLAN -> " Open your MAIFlow Profile or Billing page to manage access."
        tech.conceptualarts.maiflow.api.MAIFlowErrorKind.NETWORK,
        tech.conceptualarts.maiflow.api.MAIFlowErrorKind.RATE_LIMITED,
        tech.conceptualarts.maiflow.api.MAIFlowErrorKind.SERVER -> " Retry when the service is available."
        else -> ""
    }
    Messages.showErrorDialog(project, message + suffix, "MAIFlow")
}
