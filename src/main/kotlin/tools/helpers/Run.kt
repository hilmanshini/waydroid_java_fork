/*
 * Copyright 2021 Oliver Smith
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

object Run {
    private fun shellQuote(s: String): String =
        if (s.isEmpty()) "''"
        else if (s.none { it in " \t\n\"'\\|&;<>(){}$`!#~" }) s
        else "'${s.replace("'", "'\\''")}'"

    private fun flatCmd(cmd: List<String>, workingDir: String? = null, env: Map<String, String> = emptyMap()): String {
        val parts = env.map { (k, v) -> "$k=${shellQuote(v)}" } + cmd.map { shellQuote(it) }
        var ret = parts.joinToString(" ")
        if (workingDir != null) ret = "cd ${shellQuote(workingDir)};$ret"
        return ret
    }

    fun user(
        args: Any,
        cmd: List<String>,
        workingDir: String? = null,
        output: String = "log",
        outputReturn: Boolean = false,
        check: Boolean? = null,
        env: Map<String, String> = emptyMap(),
        sudo: Boolean = false
    ): Any {
        val finalCmd = if (env.isNotEmpty()) listOf("sh", "-c", flatCmd(cmd, env = env)) else cmd
        val msg = buildString {
            append("% ")
            env.forEach { (k, v) -> append("$k=$v ") }
            if (workingDir != null) append("cd $workingDir; ")
            append(cmd.joinToString(" "))
        }
        return RunCore.core(msg, finalCmd, workingDir, output, outputReturn, check, sudo)
    }

    fun root(
        args: Any,
        cmd: List<String>,
        workingDir: String? = null,
        output: String = "log",
        outputReturn: Boolean = false,
        check: Boolean? = null,
        env: Map<String, String> = emptyMap()
    ): Any {
        val finalCmd = if (env.isNotEmpty()) listOf("sh", "-c", flatCmd(cmd, env = env)) else cmd
        return user(args, listOf("sudo") + finalCmd, workingDir, output, outputReturn, check, env, true)
    }
}
