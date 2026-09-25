package tech.conceptualarts.maiflow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class CreateTaskAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let(::createTaskFromAction)
    }
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }
}
