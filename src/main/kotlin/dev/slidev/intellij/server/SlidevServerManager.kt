package dev.slidev.intellij.server

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import dev.slidev.intellij.settings.SlidevSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts, adopts, and stops one Slidev dev server per project, the counterpart of
 * `useDevServer` + `useServerDetector` in the VS Code extension.
 */
@Service(Service.Level.PROJECT)
class SlidevServerManager(private val project: Project, private val scope: CoroutineScope) {

    private val startJobs = ConcurrentHashMap<String, Job>()
    private val watcherStarted = AtomicBoolean()

    /**
     * Periodically probes the configured port to adopt servers started outside the IDE and to
     * release adopted ones that went away, like `useServerDetector` polling in the VS Code extension.
     */
    fun startWatcher() {
        if (!watcherStarted.compareAndSet(false, true)) {
            return
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(WATCH_INTERVAL_MS)
                watchOnce()
            }
        }
    }

    private fun watchOnce() {
        val service = SlidevProjectService.getInstance(project)

        // Release adopted servers that were stopped outside the IDE.
        for (state in service.projects()) {
            val port = state.port
            if (state.detected && port != null && ServerDetector.probe(port)?.isSlidev != true) {
                state.port = null
                state.detected = false
                state.compatMode = false
                publishServerChanged(state)
            }
        }

        val configured = SlidevSettings.getInstance(project).state.port
        if (service.projects().any { it.port == configured }) {
            return
        }
        val detection = ServerDetector.probe(configured)?.takeIf { it.isSlidev } ?: return
        // The `slidev:entry` meta tells which deck the server serves; without it (compat
        // mode servers) assume the active project.
        val state = when (val entry = detection.entry?.replace('\\', '/')) {
            null -> service.activeState()
            else -> service.projects().firstOrNull { it.entryPath == entry }
        } ?: return
        if (state.serverRunning || startJobs[state.entryPath]?.isActive == true) {
            return
        }
        state.port = configured
        state.detected = true
        state.compatMode = detection.compatMode
        if (detection.compatMode) {
            notify(SlidevBundle.message("notification.compat.mode", configured), NotificationType.WARNING)
        }
        publishServerChanged(state)
    }

    fun start(state: SlidevProjectState) {
        if (state.serverRunning) {
            return
        }
        startJobs.compute(state.entryPath) { _, previous ->
            if (previous?.isActive == true) previous
            else scope.launch(Dispatchers.IO) { doStart(state) }
        }
    }

    fun stop(state: SlidevProjectState) {
        startJobs.remove(state.entryPath)?.cancel()
        // Adopted servers were started outside the IDE and have no handler to kill.
        state.processHandler?.destroyProcess()
        state.processHandler = null
        state.port = null
        state.detected = false
        state.compatMode = false
        publishServerChanged(state)
    }

    private suspend fun doStart(state: SlidevProjectState) {
        val settings = SlidevSettings.getInstance(project).state
        val configured = settings.port

        // Adopt a server that is already serving this port, like VS Code does.
        val detection = ServerDetector.probe(configured)
        if (detection?.isSlidev == true) {
            state.port = configured
            state.detected = true
            state.compatMode = detection.compatMode
            if (detection.compatMode) {
                notify(SlidevBundle.message("notification.compat.mode", configured), NotificationType.WARNING)
            }
            publishServerChanged(state)
            return
        }

        val port = if (detection == null && ServerDetector.isPortFree(configured)) configured
        else ServerDetector.allocPort(configured + 1, usedPorts())

        val command = SlidevCommandLine.substitute(settings.devCommand, state.entryFile.name, port)
        val commandLine = PtyCommandLine(SlidevCommandLine.shellCommand(command))
            .withWorkDirectory(state.root)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        val handler = try {
            KillableColoredProcessHandler(commandLine)
        }
        catch (e: Exception) {
            notify(SlidevBundle.message("notification.server.failed") + ": " + e.message, NotificationType.ERROR)
            return
        }
        state.processHandler = handler
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                if (state.processHandler === handler) {
                    state.processHandler = null
                    state.port = null
                    state.detected = false
                    publishServerChanged(state)
                }
            }
        })

        withContext(Dispatchers.EDT) {
            if (project.isDisposed) {
                handler.destroyProcess()
                return@withContext
            }
            RunContentExecutor(project, handler)
                .withTitle(state.title)
                .withActivateToolWindow(false)
                .withStop({ stop(state) }, { !handler.isProcessTerminated })
                .run()
        }
        if (handler.isStartNotified.not()) {
            handler.startNotify()
        }

        // Poll readiness every 500 ms for ~50 s, like the VS Code extension.
        repeat(POLL_ATTEMPTS) {
            if (handler.isProcessTerminated) {
                failed(state, handler)
                return
            }
            val ready = ServerDetector.probe(port)
            if (ready?.isSlidev == true) {
                state.port = port
                state.detected = false
                state.compatMode = ready.compatMode
                if (ready.compatMode) {
                    notify(SlidevBundle.message("notification.compat.mode", port), NotificationType.WARNING)
                }
                publishServerChanged(state)
                return
            }
            delay(POLL_INTERVAL_MS)
        }
        failed(state, handler)
    }

    private fun failed(state: SlidevProjectState, handler: KillableColoredProcessHandler) {
        notify(SlidevBundle.message("notification.server.failed"), NotificationType.ERROR, showOutputAction())
        if (!handler.isProcessTerminated) {
            handler.destroyProcess()
        }
        if (state.processHandler === handler) {
            state.processHandler = null
        }
        state.port = null
        state.detected = false
        state.compatMode = false
        publishServerChanged(state)
    }

    private fun usedPorts(): Set<Int> =
        SlidevProjectService.getInstance(project).projects().mapNotNull { it.port }.toSet()

    private fun publishServerChanged(state: SlidevProjectState) {
        SlidevProjectService.getInstance(project).publish { it.serverChanged(state) }
    }

    private fun notify(content: String, type: NotificationType, action: AnAction? = null) {
        val notification = NotificationGroupManager.getInstance().getNotificationGroup("Slidev")
            .createNotification(content, type)
        if (action != null) {
            notification.addAction(action)
        }
        notification.notify(project)
    }

    /** Opens the Run tool window where the dev-server console (started via [RunContentExecutor]) lives. */
    private fun showOutputAction(): AnAction =
        NotificationAction.createSimpleExpiring(SlidevBundle.message("notification.server.show.output")) {
            ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN)?.activate(null)
        }

    companion object {
        private const val POLL_ATTEMPTS = 100
        private const val POLL_INTERVAL_MS = 500L
        private const val WATCH_INTERVAL_MS = 2000L

        @JvmStatic
        fun getInstance(project: Project): SlidevServerManager = project.service()
    }
}
