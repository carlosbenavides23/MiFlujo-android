package com.carlos.miflujo.ui.report.export

import java.text.Normalizer

/** Compare this fixture's text while keeping API block boundaries distinct from whitespace. */
internal class PdfTextComparison(blocks: List<String>) {
    private val text: String

    init {
        require(blocks.none { boundary in it })
        text = blocks.filter { it.isNotEmpty() }.joinToString(boundary.toString()) {
            decomposed(it).replace(whitespace, " ")
        }
    }

    val markers = markerPattern.findAll(text).map { it.value.replace(boundary.toString(), "") }.toList()

    fun count(literal: String) = Regex("${literalPattern(literal)}$wordEnd").findAll(text).count()

    fun incompleteLines(description: String): List<String> {
        val suffix = Regex("$wordSeparator${literalPattern(description)}$wordEnd")
        return markerPattern.findAll(text).filter { match ->
            match.value.replace(boundary.toString(), "").startsWith("LINEA") &&
                suffix.matchAt(text, match.range.last + 1) == null
        }.map { it.value.replace(boundary.toString(), "") }.toList()
    }

    private companion object {
        // A reserved noncharacter distinguishes a boundary we insert from characters in the PDF.
        const val boundary = '\uFFFF'
        const val blockJoin = "$boundary*"
        const val wordSeparator = "[ $boundary]+"
        const val wordEnd = "(?![\\p{L}\\p{M}\\p{N}])"
        val whitespace = Regex("[\\s\\p{Zs}\\p{Zl}\\p{Zp}]+")
        val markerPattern = Regex(
            "(?<![\\p{L}\\p{M}\\p{N}])(?:${literalPattern("INICIODETALLE")}|" +
                "${literalPattern("FINDETALLE")}|${literalPattern("LINEA")}(?:$blockJoin[0-9]){3})$wordEnd",
        )

        fun decomposed(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)

        fun literalPattern(literal: String) = decomposed(literal).split(' ').joinToString(wordSeparator) { word ->
            word.map { Regex.escape(it.toString()) }.joinToString(blockJoin)
        }
    }
}

internal fun String.escapePdfSample() = buildString {
    for (character in this@escapePdfSample) {
        when (character) {
            '\r' -> append("\\r")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            ' ' -> append("\\u0020")
            '\\' -> append("\\\\")
            in '!'..'~' -> append(character)
            else -> append("\\u").append(character.code.toString(16).padStart(4, '0'))
        }
    }
}
