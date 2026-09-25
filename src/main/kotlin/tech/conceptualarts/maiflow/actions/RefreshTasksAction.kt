package tech.conceptualarts.maiflow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import tech.conceptualarts.maiflow.services.MAIFlowProjectService

class RefreshTasksAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { MAIFlowProjectService.getInstance(it).refresh() }
    }
    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled = project != null && !MAIFlowProjectService.getInstance(project).isBusy()
    }
}
