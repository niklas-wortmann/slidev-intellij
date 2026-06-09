package dev.slidev.intellij.generator

import com.intellij.ide.util.projectWizard.WebTemplateNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter

/**
 * Surfaces [SlidevProjectGenerator] in IntelliJ IDEA's unified New Project wizard, which lists
 * module builders rather than `directoryProjectGenerator`s.
 */
internal class SlidevModuleBuilder :
    GeneratorNewProjectWizardBuilderAdapter(WebTemplateNewProjectWizard(SlidevProjectGenerator()))
