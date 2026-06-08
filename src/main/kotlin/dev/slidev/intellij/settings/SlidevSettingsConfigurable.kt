package dev.slidev.intellij.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevProjectService

/** Settings page under Tools | Slidev, registered in plugin.xml. */
class SlidevSettingsConfigurable(private val project: Project) :
    BoundConfigurable(SlidevBundle.message("configurable.slidev.display.name")) {

    override fun createPanel(): DialogPanel {
        val state = SlidevSettings.getInstance(project).state
        return panel {
            row(SlidevBundle.message("settings.port")) {
                intTextField(range = 1024..65535)
                    .bindIntText(state::port)
            }
            row {
                checkBox(SlidevBundle.message("settings.annotations"))
                    .bindSelected(state::annotations)
            }
            row {
                checkBox(SlidevBundle.message("settings.annotations.line.numbers"))
                    .bindSelected(state::annotationsLineNumbers)
            }
            row {
                checkBox(SlidevBundle.message("settings.preview.sync"))
                    .bindSelected(state::previewSync)
            }
            row(SlidevBundle.message("settings.include")) {
                textArea()
                    .bindText(
                        { state.include.joinToString("\n") },
                        { state.include = it.lines().map(String::trim).filter(String::isNotEmpty).toMutableList() },
                    )
                    .rows(3)
                    .columns(COLUMNS_MEDIUM)
            }
            row(SlidevBundle.message("settings.exclude")) {
                textField()
                    .bindText(state::exclude)
                    .columns(COLUMNS_MEDIUM)
            }
            row(SlidevBundle.message("settings.dev.command")) {
                textField()
                    .bindText(state::devCommand)
                    .columns(COLUMNS_LARGE)
                    .comment(SlidevBundle.message("settings.dev.command.comment"))
            }
        }
    }

    override fun apply() {
        val state = SlidevSettings.getInstance(project).state
        val includeBefore = state.include.toList()
        val excludeBefore = state.exclude
        super.apply()
        val globsChanged = includeBefore != state.include || excludeBefore != state.exclude
        SlidevProjectService.getInstance(project).onSettingsChanged(globsChanged)
    }
}
