package dev.slidev.intellij.ui.preview

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevListener
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The embedded preview: a JCEF browser hosting a small wrapper page with an iframe pointing at
 * the dev server, replicating the relay protocol of `html/ready.ts` in the VS Code extension.
 * Falls back to explanatory cards when JCEF is unavailable or no server is running.
 * Shared by the tool window and the split-editor preview; instances register themselves on
 * [SlidevPreviewService] so navigation messages reach every open preview.
 */
class SlidevPreviewComponent(
    private val project: Project,
    withToolbar: Boolean = true,
) : JPanel(BorderLayout()), Disposable {

    private val service = SlidevPreviewService.getInstance(project)
    private val gson = Gson()

    private val cards = JPanel(CardLayout())
    private var browser: JBCefBrowser? = null
    private var bridgeQuery: JBCefJSQuery? = null

    /** `port:mode` of the wrapper currently loaded, to avoid resetting the iframe on unrelated events. */
    private var loadedKey: String? = null

    @Volatile
    private var wrapperReady = false
    private val pendingMessages = mutableListOf<String>()

    init {
        if (withToolbar) {
            val toolbar = ActionManager.getInstance().createActionToolbar(
                "SlidevPreview",
                ActionManager.getInstance().getAction("Slidev.PreviewToolbar") as ActionGroup,
                true,
            )
            toolbar.targetComponent = this
            add(toolbar.component, BorderLayout.NORTH)
        }

        cards.add(messageCard(SlidevBundle.message("preview.error.no.project")), CARD_NO_PROJECT)
        cards.add(
            messageCard(
                SlidevBundle.message("preview.error.not.running"),
                SlidevBundle.message("preview.error.start"),
            ) {
                val projectService = SlidevProjectService.getInstance(project)
                projectService.activeState()?.let(projectService::startServer)
            },
            CARD_NOT_RUNNING,
        )
        cards.add(
            messageCard(
                SlidevBundle.message("preview.unsupported"),
                SlidevBundle.message("preview.unsupported.open.browser"),
            ) { service.openInBrowser() },
            CARD_UNSUPPORTED,
        )
        if (JBCefApp.isSupported()) {
            cards.add(createBrowser().component, CARD_BROWSER)
        }
        add(cards, BorderLayout.CENTER)

        service.registerPanel(this)
        project.messageBus.connect(this).subscribe(
            SlidevListener.TOPIC,
            object : SlidevListener {
                override fun projectsChanged() = updateCard()
                override fun serverChanged(state: SlidevProjectState) = updateCard()
            },
        )
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener { postTheme() },
        )
        updateCard()
    }

    private fun createBrowser(): JBCefBrowser {
        val browser = JBCefBrowser.createBuilder().setOffScreenRendering(false).build()
        browser.jbCefClient.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, JS_QUERY_POOL)
        Disposer.register(this, browser)

        val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
        Disposer.register(browser, query)
        query.addHandler { json ->
            ApplicationManager.getApplication().invokeLater { handleClientJson(json) }
            null
        }
        bridgeQuery = query

        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                    if (frame.isMain) {
                        ApplicationManager.getApplication().invokeLater { onWrapperLoaded() }
                    }
                }
            },
            browser.cefBrowser,
        )
        this.browser = browser
        return browser
    }

    // ------------------------------------------------------------------ cards

    fun updateCard() {
        val state = SlidevProjectService.getInstance(project).activeState()
        val layout = cards.layout as CardLayout
        when {
            state == null -> layout.show(cards, CARD_NO_PROJECT)
            !state.serverRunning -> layout.show(cards, CARD_NOT_RUNNING)
            browser == null -> layout.show(cards, CARD_UNSUPPORTED)
            else -> {
                layout.show(cards, CARD_BROWSER)
                loadWrapper(state)
            }
        }
    }

    /** Forces the wrapper (and so the iframe) to reload; used by refresh and mode switching. */
    fun reloadWrapper() {
        loadedKey = null
        updateCard()
    }

    // ------------------------------------------------------------------ wrapper page

    private fun loadWrapper(state: SlidevProjectState) {
        val browser = browser ?: return
        val port = state.port ?: return
        val key = "$port:${service.mode}"
        if (key == loadedKey) {
            return
        }
        loadedKey = key
        wrapperReady = false
        synchronized(pendingMessages) { pendingMessages.clear() }
        browser.loadHTML(wrapperHtml(iframeSrc(state, port)), WRAPPER_URL)
    }

    private fun iframeSrc(state: SlidevProjectState, port: Int): String {
        val hashRouter = state.data?.headmatter?.get("routerMode") == "hash"
        return if (service.mode == SlidevPreviewService.Mode.OVERVIEW) {
            val query = "mode=preview&slideNo=${service.navState.no}"
            if (hashRouter) "http://localhost:$port/?embedded=true#/overview?$query"
            else "http://localhost:$port/overview?$query&embedded=true"
        }
        else {
            "http://localhost:$port/?embedded=true"
        }
    }

    /** The counterpart of `generateReadyHtml` in the VS Code extension, bridged through [bridgeQuery]. */
    private fun wrapperHtml(iframeSrc: String): String {
        val inject = bridgeQuery?.inject("JSON.stringify(data)") ?: return ""
        // language=HTML
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <style>
              :root { --scale: 0.75; }
              html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
              iframe {
                border: none;
                width: calc(100% / var(--scale));
                height: calc(100% / var(--scale));
                transform: scale(var(--scale));
                transform-origin: 0 0;
              }
            </style>
            </head>
            <body>
            <iframe id="iframe" sandbox="allow-same-origin allow-scripts allow-popups allow-forms" src="$iframeSrc"></iframe>
            <script>
              const iframe = document.getElementById('iframe')
              window.addEventListener('message', ({ data }) => {
                if (!data || data.target !== 'slidev')
                  return
                if (data.sender === 'vscode') {
                  iframe.contentWindow.postMessage(data, '*')
                }
                else if (data.type === 'command' || data.type === 'overview-scroll' || data.type === 'open-external') {
                  $inject
                }
                else {
                  data = Object.assign({}, data, { type: 'update-state' })
                  $inject
                }
              })
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun onWrapperLoaded() {
        wrapperReady = true
        postMessage(
            "css-vars",
            mapOf("vars" to mapOf("--slidev-slide-container-background" to "transparent")),
        )
        postTheme()
        val queued = synchronized(pendingMessages) {
            val copy = pendingMessages.toList()
            pendingMessages.clear()
            copy
        }
        queued.forEach(::execute)
    }

    private fun postTheme() {
        if (browser == null) {
            return
        }
        val dark = EditorColorsManager.getInstance().isDarkEditor
        postMessage("color-schema", mapOf("color" to if (dark) "dark" else "light"))
    }

    // ------------------------------------------------------------------ messaging

    /** Posts a message to the wrapper window; the relay script forwards it into the iframe. */
    fun postMessage(type: String, payload: Map<String, Any?>) {
        if (browser == null) {
            return
        }
        val message = JsonObject()
        message.addProperty("target", "slidev")
        message.addProperty("sender", "vscode")
        message.addProperty("type", type)
        for ((key, value) in payload) {
            message.add(key, gson.toJsonTree(value))
        }
        val js = "window.postMessage($message, '*')"
        if (wrapperReady) {
            execute(js)
        }
        else {
            synchronized(pendingMessages) { pendingMessages.add(js) }
        }
    }

    private fun execute(js: String) {
        val browser = browser ?: return
        browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
    }

    private fun handleClientJson(json: String) {
        if (project.isDisposed) {
            return
        }
        val data = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return
        }
        catch (_: Exception) {
            return
        }
        service.onClientMessage(data)
    }

    // ------------------------------------------------------------------ helpers

    private fun messageCard(text: String, linkText: String? = null, onLink: (() -> Unit)? = null): JComponent {
        val panel = JPanel(GridBagLayout())
        val box = Box.createVerticalBox()
        val label = JBLabel(text, SwingConstants.CENTER)
        label.alignmentX = CENTER_ALIGNMENT
        box.add(label)
        if (linkText != null && onLink != null) {
            box.add(Box.createVerticalStrut(8))
            val link = ActionLink(linkText) { onLink() }
            link.alignmentX = CENTER_ALIGNMENT
            box.add(link)
        }
        panel.add(box)
        return panel
    }

    override fun dispose() {
        service.unregisterPanel(this)
    }

    companion object {
        private const val CARD_NO_PROJECT = "noProject"
        private const val CARD_NOT_RUNNING = "notRunning"
        private const val CARD_UNSUPPORTED = "unsupported"
        private const val CARD_BROWSER = "browser"
        private const val WRAPPER_URL = "http://slidev.ide/preview"
        private const val JS_QUERY_POOL = 8
    }
}
