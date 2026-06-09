package dev.slidev.intellij.generator

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.ide.util.projectWizard.WebProjectTemplate
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ProjectGeneratorPeer
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.SlidevIcons
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.server.SlidevCommandLine
import java.io.IOException
import javax.swing.Icon

/**
 * "Slidev" entry in the New Project wizard: scaffolds the bundled `create-slidev` starter template
 * (see [SlidevStarterTemplate]), opens `slides.md`, and optionally installs dependencies.
 *
 * Registered twice in plugin.xml: as a `directoryProjectGenerator` (WebStorm and other small-IDE
 * dialogs) and via [SlidevModuleBuilder] (IntelliJ IDEA's unified wizard).
 */
internal class SlidevProjectGenerator : WebProjectTemplate<SlidevGeneratorSettings>() {

    override fun getName(): String = SlidevBundle.message("generator.slidev.name")

    override fun getDescription(): String = SlidevBundle.message("generator.slidev.description")

    override fun getLogo(): Icon = SlidevIcons.Logo

    // WebTemplateNewProjectWizard (the IDEA unified-wizard path) reads getIcon(), whose
    // WebProjectTemplate default is the generic web globe — it does NOT delegate to getLogo().
    override fun getIcon(): Icon = SlidevIcons.Logo

    override fun createPeer(): ProjectGeneratorPeer<SlidevGeneratorSettings> = SlidevGeneratorPeer()

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: SlidevGeneratorSettings,
        module: Module,
    ) {
        try {
            SlidevStarterTemplate.writeTo(baseDir.toNioPath(), baseDir.name, settings.packageManager)
        }
        catch (e: IOException) {
            notify(project, SlidevBundle.message("notification.generator.write.failed", e.message ?: e.toString()))
            return
        }
        // Synchronous refresh so the children are visible right away (we are on the EDT here).
        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)

        baseDir.findChild("slides.md")?.let { FileEditorManager.getInstance(project).openFile(it, true) }
        // The VFS listener usually picks the new entry up; rescan explicitly to cover the IDEA flow
        // where the startup scan already ran against the empty directory.
        SlidevProjectService.getInstance(project).rescan()

        if (settings.installDependencies) {
            installDependencies(project, baseDir, settings.packageManager)
        }
    }

    /** Runs `<pm> install` in the Run tool window, like the dev server in `SlidevServerManager`. */
    private fun installDependencies(project: Project, baseDir: VirtualFile, packageManager: PackageManager) {
        val commandLine = PtyCommandLine(SlidevCommandLine.shellCommand(packageManager.installCommand))
            .withWorkDirectory(baseDir.path)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        val handler = try {
            KillableColoredProcessHandler(commandLine)
        }
        catch (e: Exception) {
            notify(project, SlidevBundle.message("notification.generator.install.failed", e.message ?: e.toString()))
            return
        }
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                // Make node_modules (and lockfile) show up without a manual synchronize.
                baseDir.refresh(true, true)
                if (event.exitCode != 0) {
                    notify(project, SlidevBundle.message("notification.generator.install.failed", event.exitCode))
                }
            }
        })
        RunContentExecutor(project, handler)
            .withTitle(SlidevBundle.message("generator.slidev.install.title"))
            .withActivateToolWindow(true)
            .run()
        if (!handler.isStartNotified) {
            handler.startNotify()
        }
    }

    private fun notify(project: Project, content: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("Slidev")
            .createNotification(content, NotificationType.ERROR)
            .notify(project)
    }
}
