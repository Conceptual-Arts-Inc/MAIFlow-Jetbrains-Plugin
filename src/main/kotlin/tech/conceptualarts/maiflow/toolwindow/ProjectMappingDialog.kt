package tech.conceptualarts.maiflow.toolwindow

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import tech.conceptualarts.maiflow.model.FlowSummary
import tech.conceptualarts.maiflow.model.ProjectSummary
import tech.conceptualarts.maiflow.services.MAIFlowProjectMappingState
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class ProjectMappingDialog(
    private val flows: List<FlowSummary>,
    private val projects: List<ProjectSummary>,
    mapping: MAIFlowProjectMappingState,
) : DialogWrapper(true) {
    private val flowCombo = ComboBox<FlowSummary>()
    private val projectCombo = ComboBox<ProjectChoice>()
    private val useCheck = JCheckBox("Use this mapping for new tasks", mapping.useForNewTasks)

    init {
        title = "Map IDE project to MAIFlow"
        flowCombo.model = DefaultComboBoxModel(flows.toTypedArray())
        flowCombo.renderer = renderer { (it as? FlowSummary)?.name ?: "Select a flow" }
        flowCombo.selectedItem = flows.firstOrNull { it.id == mapping.flowId } ?: flows.firstOrNull()
        val choices = listOf(ProjectChoice(null, "No project")) + projects.map { ProjectChoice(it, it.name) }
        projectCombo.model = DefaultComboBoxModel(choices.toTypedArray())
        projectCombo.renderer = renderer { (it as? ProjectChoice)?.label ?: "No project" }
        projectCombo.selectedItem = choices.firstOrNull { it.project?.id == mapping.projectId } ?: choices.first()
        init()
    }

    fun mapping(): MAIFlowProjectMappingState = MAIFlowProjectMappingState(
        flowId = (flowCombo.selectedItem as? FlowSummary)?.id.orEmpty(),
        projectId = (projectCombo.selectedItem as? ProjectChoice)?.project?.id.orEmpty(),
        useForNewTasks = useCheck.isSelected,
    )

    override fun createCenterPanel(): JComponent = JPanel(GridBagLayout()).apply {
        add(JBLabel("Flow"), constraints(0))
        add(flowCombo, constraints(1).apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 })
        add(JBLabel("Project"), constraints(2))
        add(projectCombo, constraints(3).apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 })
        add(useCheck, constraints(4).apply { gridx = 1; gridwidth = 2; anchor = GridBagConstraints.WEST })
    }

    private fun constraints(row: Int) = GridBagConstraints().apply {
        gridx = if (row % 2 == 0) 0 else 1
        gridy = row / 2
        anchor = GridBagConstraints.NORTHWEST
        insets = Insets(4, 4, 4, 8)
    }

    private fun renderer(textFor: (Any?) -> String) = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, selected, focus); text = textFor(value); return this
        }
    }

    data class ProjectChoice(val project: ProjectSummary?, val label: String)
}
