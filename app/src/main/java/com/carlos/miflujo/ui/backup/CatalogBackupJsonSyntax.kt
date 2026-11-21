package com.carlos.miflujo.ui.backup

import java.util.regex.Pattern
import org.json.JSONTokener

/**
 * The Android and JVM org.json readers differ in what malformed JSON they accept. Check
 * syntax and duplicate member names before either runtime can reinterpret the document.
 */
internal fun validateCatalogBackupJsonSyntax(json: String) {
    val tokens = CatalogBackupTokens(json)
    tokens.readValue()
    tokens.requireEnd()
}

private class CatalogBackupTokens(private val json: String) {
    private val matcher = tokenPattern.matcher(json)
    private var offset = 0
    private var token = nextToken()

    fun readValue(depth: Int = 0) {
        require(depth <= 64) { "Backup JSON is nested too deeply" }
        when (token?.firstOrNull()) {
            '{' -> {
                take("{")
                val fields = mutableSetOf<String>()
                if (!take("}")) {
                    do {
                        val name = requireNotNull(token)
                        require(name.startsWith('"')) { "Expected a JSON member name" }
                        require(fields.add(JSONTokener(name).nextValue() as String)) { "Duplicate JSON member" }
                        token = nextToken()
                        expect(":")
                        readValue(depth + 1)
                    } while (take(","))
                    expect("}")
                }
            }
            '[' -> {
                take("[")
                if (!take("]")) {
                    do {
                        readValue(depth + 1)
                    } while (take(","))
                    expect("]")
                }
            }
            null, '}', ']', ',', ':' -> error("Expected a JSON value")
            else -> token = nextToken()
        }
    }

    fun requireEnd() {
        require(token == null) { "Trailing JSON content" }
    }

    private fun take(expected: String): Boolean {
        if (token != expected) return false
        token = nextToken()
        return true
    }

    private fun expect(expected: String) {
        require(take(expected)) { "Expected '$expected'" }
    }

    private fun nextToken(): String? {
        while (offset < json.length && json[offset] in " \t\r\n") offset++
        if (offset == json.length) return null
        matcher.region(offset, json.length)
        require(matcher.lookingAt()) { "Invalid JSON token" }
        offset = matcher.end()
        return matcher.group()
    }

    private companion object {
        val tokenPattern: Pattern = Pattern.compile(
            """[{}\[\],:]|"(?:[^"\\\x00-\x1f]++|\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))*+"|""" +
                """-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|true|false|null""",
        )
    }
}
