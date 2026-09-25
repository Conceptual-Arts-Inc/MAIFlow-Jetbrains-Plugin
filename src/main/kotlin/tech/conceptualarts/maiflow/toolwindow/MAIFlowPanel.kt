package tech.conceptualarts.maiflow.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import tech.conceptualarts.maiflow.api.EndpointIdentity
import tech.conceptualarts.maiflow.api.MAIFlowApiError
import tech.conceptualarts.maiflow.model.CreateTaskRequest
import tech.conceptualarts.maiflow.model.FlowSummary
import tech.conceptualarts.maiflow.model.ProjectViewState
import tech.conceptualarts.maiflow.model.ProjectSummary
import tech.conceptualarts.maiflow.model.TaskSummary
import tech.conceptualarts.maiflow.model.WorkspaceSnapshot
import tech.conceptualarts.maiflow.services.MAIFlowApplicationSettings
import tech.conceptualarts.maiflow.services.MAIFlowProjectService
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListCellRenderer

class MAIFlowPanel(
    private val project: Project,
    private val service: MAIFlowProjectService,
) : JPanel(BorderLayout()), com.intellij.openapi.Disposable {
    private val refreshButton = JButton("Refresh")
    private val createButton = JButton("Create task")
    private val settingsButton = JButton("Settings")
    private val mappingButton = JButton("Map project")
    private val openButton = JButton("Open selection")
    private val configureButton = JButton("Configure MAIFlow")
    private val retryButton = JButton("Retry")
    private val stateLabel = JBLabel()
    private val flowFilter = JComboBox<FlowChoice>()
    private val projectFilter = JComboBox<ProjectChoice>()
    private val taskList = JBList<TaskSummary>()
    private val detail = TaskDetailPanel(service) { message -> stateLabel.text = message }
    private val cardPanel = JPanel(CardLayout())
    private val emptyLabel = JBLabel("No reachable MAIFlow tasks were returned.")
    private var workspace: WorkspaceSnapshot? = null
    private var listener: ((ProjectViewState) -> Unit)? = null
    private var busyListener: ((Boolean) -> Unit)? = null

    init {
        border = JBUI.Borders.empty(4)
        buildToolbar()
        taskList.cellRenderer = taskRenderer()
        taskList.addListSelectionListener {
            val selected = taskList.selectedValue
            service.setSelectedTask(selected)
            detail.showTask(selected)
            openButton.isEnabled = selected != null
        }
        taskList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && taskList.selectedValue != null) openSelection()
            }
        })
        taskList.fixedCellHeight = 56
        taskList.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        val listScroll = JBScrollPane(taskList).apply {
            border = JBUI.Borders.emptyTop(4)
        }
        cardPanel.add(listScroll, "list")
        cardPanel.add(emptyLabel, "empty")
        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, cardPanel, JBScrollPane(detail)).apply {
            resizeWeight = 0.55
            border = null
        }
        add(split, BorderLayout.CENTER)
        add(stateLabel, BorderLayout.SOUTH)
        listener = { render(it) }
        service.addListener(listener!!)
        busyListener = { applyBusy(it) }
        service.addBusyListener(busyListener!!)
        service.refresh()
    }

    private fun buildToolbar() {
        val toolbar = JPanel(BorderLayout(0, 4)).apply {
            border = JBUI.Borders.emptyBottom(4)
        }
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val filters = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        refreshButton.addActionListener { service.refresh() }
        createButton.addActionListener { createTask() }
        settingsButton.addActionListener { openSettings() }
        mappingButton.addActionListener { mapProject() }
        openButton.addActionListener { openSelection() }
        configureButton.addActionListener { openSettings() }
        retryButton.addActionListener { service.refresh() }
        openButton.isEnabled = false
        refreshButton.toolTipText = "Reload tasks from MAIFlow"
        createButton.toolTipText = "Create a task in MAIFlow"
        openButton.toolTipText = "Open the selected task in your browser"
        actions.add(refreshButton); actions.add(createButton); actions.add(openButton)
        actions.add(mappingButton); actions.add(settingsButton)
        actions.add(configureButton); actions.add(retryButton)
        filters.add(JBLabel("Filter:"))
        filters.add(flowFilter); filters.add(projectFilter)
        toolbar.add(actions, BorderLayout.NORTH)
        toolbar.add(filters, BorderLayout.SOUTH)
        add(toolbar, BorderLayout.NORTH)
        flowFilter.addActionListener { renderTasks() }
        projectFilter.addActionListener { renderTasks() }
        setFilterModels(null)
    }

    private fun render(state: ProjectViewState) {
        when (state) {
            ProjectViewState.NotConfigured -> {
                workspace = null
                setFilterModels(null)
                showCard("empty")
                emptyLabel.text = "MAIFlow is not configured for this IDE."
                stateLabel.text = "Add your server URL and API token to get started."
                configureButton.isVisible = true
                retryButton.isVisible = false
                applyBusy(false)
            }
            is ProjectViewState.Loading -> {
                state.cached?.let { workspace = it; setFilterModels(it); renderTasks() }
                stateLabel.text = "Loading MAIFlow tasks…"
                retryButton.isVisible = false
                applyBusy(true)
            }
            is ProjectViewState.Loaded -> showLoaded(state.snapshot, "")
            is ProjectViewState.Empty -> {
                showLoaded(state.snapshot, "No reachable MAIFlow tasks were returned.")
                showCard("empty")
            }
            is ProjectViewState.Stale -> {
                showLoaded(state.snapshot, "Showing cached data. ${state.error}")
                retryButton.isVisible = true
                applyBusy(false)
            }
            is ProjectViewState.Failed -> {
                state.cached?.let { showLoaded(it, "") } ?: run { workspace = null; setFilterModels(null); showCard("empty") }
                emptyLabel.text = state.error
                stateLabel.text = state.error
                retryButton.isVisible = true
                configureButton.isVisible = state.error.contains("token", ignoreCase = true) || state.error.contains("Settings", ignoreCase = true)
                applyBusy(false)
            }
        }
    }

    private fun showLoaded(snapshot: WorkspaceSnapshot, message: String) {
        workspace = snapshot
        setFilterModels(snapshot)
        renderTasks()
        stateLabel.text = message.ifBlank { "${snapshot.tasks.size} task${if (snapshot.tasks.size == 1) "" else "s"}" }
        retryButton.isVisible = message.isNotBlank()
        configureButton.isVisible = false
        applyBusy(false)
    }

    private fun applyBusy(value: Boolean) {
        refreshButton.isEnabled = !value
        createButton.isEnabled = !value && workspace?.flows?.isNotEmpty() == true
        flowFilter.isEnabled = !value
        projectFilter.isEnabled = !value
    }

    private fun setFilterModels(snapshot: WorkspaceSnapshot?) {
        val selectedFlow = (flowFilter.selectedItem as? FlowChoice)?.id
        val selectedProject = (projectFilter.selectedItem as? ProjectChoice)?.id
        val flows = listOf(FlowChoice(null, "All flows")) + (snapshot?.flows ?: emptyList()).map { FlowChoice(it.id, it.name) }
        val projects = listOf(ProjectChoice(null, "All projects")) + (snapshot?.projects ?: emptyList()).map { ProjectChoice(it.id, it.name) }
        flowFilter.model = DefaultComboBoxModel(flows.toTypedArray())
        projectFilter.model = DefaultComboBoxModel(projects.toTypedArray())
        flowFilter.renderer = renderer { (it as? FlowChoice)?.label ?: "All flows" }
        projectFilter.renderer = renderer { (it as? ProjectChoice)?.label ?: "All projects" }
        flowFilter.selectedItem = flows.firstOrNull { it.id == selectedFlow } ?: flows.first()
        projectFilter.selectedItem = projects.firstOrNull { it.id == selectedProject } ?: projects.first()
    }

    private fun renderTasks() {
        val current = workspace ?: return
        val flowId = (flowFilter.selectedItem as? FlowChoice)?.id
        val projectId = (projectFilter.selectedItem as? ProjectChoice)?.id
        val filtered = current.tasks.filter {
            (flowId == null || it.flowId == flowId) && (projectId == null || it.projectId == projectId)
        }
        taskList.setListData(filtered.toTypedArray())
        if (filtered.isEmpty()) {
            emptyLabel.text = if (current.tasks.isEmpty()) "No reachable MAIFlow tasks were returned." else "No tasks match the current filters."
            showCard("empty")
        } else showCard("list")
    }

    private fun createTask() {
        val current = workspace ?: return
        val mapping = service.mapping()
        val dialog = CreateTaskDialog(
            current,
            mapping.flowId.takeIf { mapping.useForNewTasks }.orEmpty(),
            mapping.projectId.takeIf { mapping.useForNewTasks }.orEmpty(),
        )
        if (!dialog.showAndGet()) return
        val request = dialog.request()
        service.createTask(request) { result ->
            result.onSuccess { stateLabel.text = "Task created successfully." }
                .onFailure { stateLabel.text = it.message ?: "Could not create the task." }
        }
    }

    private fun mapProject() {
        val current = workspace ?: return
        val dialog = ProjectMappingDialog(current.flows, current.projects, service.mapping())
        if (dialog.showAndGet()) {
            val value = dialog.mapping()
            service.setMapping(value.flowId, value.projectId, value.useForNewTasks)
            stateLabel.text = if (value.flowId.isBlank()) "Project mapping cleared." else "Project mapping saved."
        }
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "MAIFlow")
    }

    private fun openSelection() {
        val task = service.selectedTask() ?: return
        val endpoint = MAIFlowApplicationSettings.getInstance().serverUrl
        BrowserUtil.browse("${EndpointIdentity.origin(endpoint)}/tasks/${java.net.URLEncoder.encode(task.id, Charsets.UTF_8)}")
    }

    private fun showCard(name: String) {
        (cardPanel.layout as CardLayout).show(cardPanel, name)
    }

    private fun taskRenderer(): ListCellRenderer<TaskSummary> = ListCellRenderer { list, value, _, selected, _ ->
        val task = value ?: return@ListCellRenderer JPanel()
        val primary = JBLabel(task.title.ifBlank { "Untitled task" }).apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD)
        }
        val context = listOfNotNull(task.flowName, task.projectName).joinToString("  ·  ")
        val secondary = JBLabel("${task.status.displayName}  ·  $context").apply {
            foreground = if (selected) list.selectionForeground else com.intellij.ui.JBColor.GRAY
        }
        val score = JBLabel("Score ${task.engineScore}").apply {
            foreground = if (selected) list.selectionForeground else task.statusColor()
            horizontalAlignment = javax.swing.SwingConstants.RIGHT
        }
        JPanel(GridBagLayout()).apply {
            isOpaque = true
            background = if (selected) list.selectionBackground else list.background
            border = JBUI.Borders.empty(6, 8)
            add(primary, GridBagConstraints().apply { gridx = 0; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST })
            add(score, GridBagConstraints().apply { gridx = 1; gridy = 0; anchor = GridBagConstraints.EAST; insets = Insets(0, 8, 0, 0) })
            add(secondary, GridBagConstraints().apply { gridx = 0; gridy = 1; gridwidth = 2; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST; insets = Insets(3, 0, 0, 0) })
        }
    }

    private fun TaskSummary.statusColor(): Color = when (status) {
        tech.conceptualarts.maiflow.model.TaskStatus.IN_PROGRESS -> Color(0x35, 0x7A, 0xC4)
        tech.conceptualarts.maiflow.model.TaskStatus.BLOCKED -> Color(0xC7, 0x5C, 0x1E)
        tech.conceptualarts.maiflow.model.TaskStatus.COMPLETED -> Color(0x4E, 0x9A, 0x5B)
        tech.conceptualarts.maiflow.model.TaskStatus.TODO -> Color(0x6B, 0x6B, 0x6B)
    }

    private fun renderer(textFor: (Any?) -> String) = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, selected, focus); text = textFor(value); return this
        }
    }

    override fun dispose() {
        listener?.let { service.removeListener(it) }
        busyListener?.let { service.removeBusyListener(it) }
        listener = null
        busyListener = null
    }

    data class FlowChoice(val id: String?, val label: String)
    data class ProjectChoice(val id: String?, val label: String)
}
