package dev.slidev.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object SlidevIcons {
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/slidev.svg", SlidevIcons::class.java)

    /** The Slidev logo, shown on markdown files registered as Slidev entries. */
    @JvmField
    val File: Icon = IconLoader.getIcon("/icons/slidevFile.svg", SlidevIcons::class.java)

    /** The 16x16 logo for the New Project wizard generator. */
    @JvmField
    val Logo: Icon = IconLoader.getIcon("/icons/slidevFile.svg", SlidevIcons::class.java)
}
