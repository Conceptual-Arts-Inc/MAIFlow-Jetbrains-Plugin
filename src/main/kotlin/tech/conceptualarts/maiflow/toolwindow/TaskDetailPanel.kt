package tech.conceptualarts.maiflow.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import tech.conceptualarts.maiflow.api.EndpointIdentity
import tech.conceptualarts.maiflow.model.TaskStatus
import tech.conceptualarts.maiflow.model.TaskSummary
import tech.conceptualarts.maiflow.model.UpdateTaskRequest
import tech.conceptualarts.maiflow.services.MAIFlowApplicationSettings
import tech.conceptualarts.maiflow.services.MAIFlowProjectService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class TaskDetailPanel(
    private val service: MAIFlowProjectService,
    private val onMessage: (String) -> Unit,
) : JPanel(GridBagLayout()) {
    private val titleField = JBTextField()
    private val descriptionArea = JBTextArea(5, 40).apply { lineWrap = true; wrapStyleWord = true }
    private val statusCombo = ComboBox(TaskStatus.entries.toTypedArray())
    private val prioritySpinner = scoreSpinner()
    private val urgencySpinner = scoreSpinner()
    private val impactSpinner = scoreSpinner()
    private val effortSpinner = scoreSpinner()
    private val idLabel = JBLabel()
    private val flowLabel = JBLabel()
    private val projectLabel = JBLabel()
    private val assigneeLabel = JBLabel()
    private val engineScoreLabel = JBLabel()
    private var task: TaskSummary? = null

    init {
        border = JBUI.Borders.empty(8)
        add(JBLabel("Select a task to see its details."), GridBagConstraints().apply {
            gridx = 0; gridy = 0; gridwidth = 2; anchor = GridBagConstraints.NORTHWEST
        })
        isVisible = true
    }

    fun showTask(value: TaskSummary?) {
        task = value
        removeAll()
        if (value == null) {
            add(JBLabel("Select a task to see its details."), labelConstraints(0))
            revalidate(); repaint(); return
        }
        idLabel.text = "ID: ${value.id}"
        titleField.text = value.title
        descriptionArea.text = value.description
        statusCombo.selectedItem = value.status
        prioritySpinner.value = value.priority
        urgencySpinner.value = value.urgency
        impactSpinner.value = value.impact
        effortSpinner.value = value.effortPoints
        flowLabel.text = value.flowName
        projectLabel.text = value.projectName ?: "No project"
        assigneeLabel.text = value.assignee?.name ?: "Unassigned"
        engineScoreLabel.text = value.engineScore.toString()

        var row = 0
        add(JBLabel("Task details"), titleConstraints(row++))
        add(idLabel, pairConstraints(0, row++))
        addField("Title", titleField, row++)
        addField("Description", JBScrollPane(descriptionArea), row++)
        addField("Status", statusCombo, row++)
        addField("Priority (0–10)", prioritySpinner, row++)
        addField("Urgency (0–10)", urgencySpinner, row++)
        addField("Impact (0–10)", impactSpinner, row++)
        addField("Effort (0–10)", effortSpinner, row++)
        addReadOnly("Flow", flowLabel, row++)
        addReadOnly("Project", projectLabel, row++)
        addReadOnly("Assignee", assigneeLabel, row++)
        addReadOnly("Engine score", engineScoreLabel, row++)

        val buttons = JPanel().apply {
            add(JButton("Save changes").apply { addActionListener { save() } })
            add(JButton("Open in MAIFlow").apply { addActionListener { openInMaiflow() } })
        }
        add(buttons, pairConstraints(0, row).apply { gridwidth = 2; anchor = GridBagConstraints.WEST })
        revalidate(); repaint()
    }

    private fun save() {
        val current = task ?: return
        val selectedStatus = statusCombo.selectedItem as? TaskStatus ?: current.status
        val request = UpdateTaskRequest(
            title = titleField.text,
            description = descriptionArea.text,
            status = selectedStatus,
            priority = prioritySpinner.value as Int,
            urgency = urgencySpinner.value as Int,
            impact = impactSpinner.value as Int,
            effortPoints = effortSpinner.value as Int,
        )
        service.updateTask(current.id, request) { result ->
            result.onSuccess { onMessage("Task updated successfully.") }
                .onFailure { onMessage(it.message ?: "Could not update the task.") }
        }
    }

    private fun openInMaiflow() {
        val current = task ?: return
        val endpoint = MAIFlowApplicationSettings.getInstance().serverUrl
        BrowserUtil.browse("${EndpointIdentity.origin(endpoint)}/tasks/${java.net.URLEncoder.encode(current.id, Charsets.UTF_8)}")
    }

    private fun addField(label: String, component: javax.swing.JComponent, row: Int) {
        add(JBLabel(label), labelConstraints(row))
        add(component, pairConstraints(1, row).apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 })
    }

    private fun addReadOnly(label: String, component: javax.swing.JComponent, row: Int) {
        component.foreground = com.intellij.ui.JBColor.GRAY
        addField(label, component, row)
    }

    private fun scoreSpinner() = JSpinner(SpinnerNumberModel(0, 0, 10, 1))

    private fun titleConstraints(row: Int) = pairConstraints(0, row).apply { gridwidth = 2; insets = Insets(4, 0, 8, 0) }
    private fun labelConstraints(row: Int) = pairConstraints(0, row).apply { insets = Insets(3, 0, 3, 8) }
    private fun pairConstraints(column: Int, row: Int) = GridBagConstraints().apply {
        gridx = column; gridy = row; anchor = GridBagConstraints.NORTHWEST; insets = Insets(3, 0, 3, 8)
    }
}
