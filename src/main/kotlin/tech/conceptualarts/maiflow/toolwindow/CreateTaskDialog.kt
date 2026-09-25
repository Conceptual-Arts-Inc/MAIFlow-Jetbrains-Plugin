package tech.conceptualarts.maiflow.toolwindow

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import tech.conceptualarts.maiflow.model.CreateTaskRequest
import tech.conceptualarts.maiflow.model.FlowSummary
import tech.conceptualarts.maiflow.model.ProjectSummary
import tech.conceptualarts.maiflow.model.WorkspaceSnapshot
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class CreateTaskDialog(
    private val workspace: WorkspaceSnapshot,
    private val preferredFlowId: String = "",
    private val preferredProjectId: String = "",
) : DialogWrapper(true) {
    private val titleField = JBTextField()
    private val descriptionArea = JBTextArea(5, 40).apply { lineWrap = true; wrapStyleWord = true }
    private val flowCombo = ComboBox<FlowSummary>()
    private val projectCombo = ComboBox<ProjectChoice>()
    private val prioritySpinner = JSpinner(SpinnerNumberModel(5, 0, 10, 1))
    private val urgencySpinner = JSpinner(SpinnerNumberModel(5, 0, 10, 1))
    private val impactSpinner = JSpinner(SpinnerNumberModel(5, 0, 10, 1))
    private val effortSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))

    init {
        title = "Create MAIFlow task"
        flowCombo.model = DefaultComboBoxModel(workspace.flows.toTypedArray())
        flowCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, selected, focus)
                text = (value as? FlowSummary)?.name ?: "Select a flow"
                return this
            }
        }
        flowCombo.selectedItem = workspace.flows.firstOrNull { it.id == preferredFlowId } ?: workspace.flows.firstOrNull()
        val choices = listOf(ProjectChoice(null, "No project")) + workspace.projects.map { ProjectChoice(it, it.name) }
        projectCombo.model = DefaultComboBoxModel(choices.toTypedArray())
        projectCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, selected, focus)
                text = (value as? ProjectChoice)?.label ?: "No project"
                return this
            }
        }
        projectCombo.selectedItem = choices.firstOrNull { it.project?.id == preferredProjectId } ?: choices.first()
        init()
    }

    fun request(): CreateTaskRequest = CreateTaskRequest(
        title = titleField.text,
        description = descriptionArea.text,
        flowId = (flowCombo.selectedItem as? FlowSummary)?.id.orEmpty(),
        projectId = (projectCombo.selectedItem as? ProjectChoice)?.project?.id,
        priority = prioritySpinner.value as Int,
        urgency = urgencySpinner.value as Int,
        impact = impactSpinner.value as Int,
        effortPoints = effortSpinner.value as Int,
    )

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        var row = 0
        addField(panel, "Title", titleField, row++)
        addField(panel, "Description", JBScrollPane(descriptionArea), row++)
        addField(panel, "Flow", flowCombo, row++)
        addField(panel, "Project", projectCombo, row++)
        addField(panel, "Priority (0–10)", prioritySpinner, row++)
        addField(panel, "Urgency (0–10)", urgencySpinner, row++)
        addField(panel, "Impact (0–10)", impactSpinner, row++)
        addField(panel, "Effort (0–10)", effortSpinner, row++)
        return panel
    }

    private fun addField(panel: JPanel, label: String, component: JComponent, row: Int) {
        panel.add(JBLabel(label), GridBagConstraints().apply {
            gridx = 0; gridy = row; anchor = GridBagConstraints.NORTHWEST; insets = Insets(4, 4, 4, 10)
        })
        panel.add(component, GridBagConstraints().apply {
            gridx = 1; gridy = row; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; insets = Insets(4, 4, 4, 4)
        })
    }

    data class ProjectChoice(val project: ProjectSummary?, val label: String)
}
