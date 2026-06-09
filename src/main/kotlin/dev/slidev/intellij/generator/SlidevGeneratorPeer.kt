package dev.slidev.intellij.generator

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import dev.slidev.intellij.SlidevBundle
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The generator options below the location field: package manager and whether to run the install
 * right after scaffolding. Both render paths must be implemented because IDEA's unified wizard only
 * calls [buildUI] while the small-IDE (WebStorm) dialog only calls [getComponent].
 */
internal class SlidevGeneratorPeer : ProjectGeneratorPeer<SlidevGeneratorSettings> {

    private val packageManagerCombo = ComboBox(EnumComboBoxModel(PackageManager::class.java))
    private val installCheckBox = JBCheckBox(SlidevBundle.message("generator.slidev.install.dependencies"), true)

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(SlidevBundle.message("generator.slidev.package.manager"), packageManagerCombo)
        .addComponent(installCheckBox)
        .panel

    override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent = panel

    override fun buildUI(settingsStep: SettingsStep) {
        settingsStep.addSettingsField(SlidevBundle.message("generator.slidev.package.manager"), packageManagerCombo)
        settingsStep.addSettingsComponent(installCheckBox)
    }

    override fun getSettings(): SlidevGeneratorSettings =
        SlidevGeneratorSettings(packageManagerCombo.item, installCheckBox.isSelected)

    override fun validate(): ValidationInfo? = null

    override fun isBackgroundJobRunning(): Boolean = false
}
