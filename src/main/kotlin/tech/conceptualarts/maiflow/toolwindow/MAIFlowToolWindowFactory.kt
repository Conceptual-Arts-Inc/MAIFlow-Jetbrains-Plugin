package tech.conceptualarts.maiflow.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import tech.conceptualarts.maiflow.services.MAIFlowApplicationSettings
import tech.conceptualarts.maiflow.services.MAIFlowProjectService

class MAIFlowToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project): Boolean = MAIFlowApplicationSettings.getInstance().isConfigured()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MAIFlowPanel(project, MAIFlowProjectService.getInstance(project))
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
