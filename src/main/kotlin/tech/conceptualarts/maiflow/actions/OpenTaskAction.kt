package tech.conceptualarts.maiflow.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import tech.conceptualarts.maiflow.api.EndpointIdentity
import tech.conceptualarts.maiflow.services.MAIFlowApplicationSettings
import tech.conceptualarts.maiflow.services.MAIFlowProjectService

class OpenTaskAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val task = MAIFlowProjectService.getInstance(project).selectedTask() ?: return
        val endpoint = MAIFlowApplicationSettings.getInstance().serverUrl
        BrowserUtil.browse("${EndpointIdentity.origin(endpoint)}/tasks/${java.net.URLEncoder.encode(task.id, Charsets.UTF_8)}")
    }
    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled = project != null && MAIFlowProjectService.getInstance(project).selectedTask() != null
    }
}
