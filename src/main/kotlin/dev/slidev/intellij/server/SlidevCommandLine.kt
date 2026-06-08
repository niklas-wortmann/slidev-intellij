package dev.slidev.intellij.server

import com.intellij.openapi.util.SystemInfo

/**
 * Builds the dev-server command from the user's command template, the counterpart of
 * the `${args}`/`${port}` substitution in `useDevServer` of the VS Code extension.
 */
object SlidevCommandLine {

    /** Replaces `${args}` with the quoted entry file name plus `--port`, and `${port}` with the port. */
    fun substitute(devCommand: String, entryFileName: String, port: Int): String =
        devCommand
            .replace("\${args}", "\"$entryFileName\" --port $port")
            .replace("\${port}", port.toString())

    /** Wraps a shell command string for execution, like VS Code's terminal `sendText` does implicitly. */
    fun shellCommand(command: String, isWindows: Boolean = SystemInfo.isWindows): List<String> =
        if (isWindows) {
            listOf("cmd.exe", "/c", command)
        }
        else {
            listOf(System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: "/bin/sh", "-c", command)
        }
}
