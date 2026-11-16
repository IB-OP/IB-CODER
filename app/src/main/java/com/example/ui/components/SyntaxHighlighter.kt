package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object SyntaxHighlighter {

    data class SyntaxPalette(
        val plain: Color,
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val annotation: Color,
        val type: Color
    )

    val DarkPalette = SyntaxPalette(
        plain = Color(0xFFF1F5F9),
        keyword = Color(0xFFFFFFFF),
        string = Color(0xFF93C5FD),
        comment = Color(0xFF6B7280),
        number = Color(0xFFFCD34D),
        annotation = Color(0xFF6EE7B7),
        type = Color(0xFFCBD5E1)
    )

    val LightPalette = SyntaxPalette(
        plain = Color(0xFF14181F),
        keyword = Color(0xFF0F172A),
        string = Color(0xFF0369A1),
        comment = Color(0xFF64748B),
        number = Color(0xFFB45309),
        annotation = Color(0xFF0F766E),
        type = Color(0xFF334155)
    )

    private val kotlinJavaKeywords = setOf(
        "package", "import", "class", "interface", "object", "enum", "sealed", "data",
        "abstract", "open", "override", "private", "protected", "public", "internal",
        "fun", "val", "var", "const", "lateinit", "suspend", "inline", "reified",
        "return", "if", "else", "when", "for", "while", "do", "break", "continue",
        "try", "catch", "finally", "throw", "in", "is", "as", "by", "companion",
        "init", "this", "super", "true", "false", "null", "void", "static", "final",
        "extends", "implements", "new", "switch", "case", "default", "plugins",
        "dependencies", "implementation", "testImplementation", "androidTestImplementation",
        "alias", "android", "defaultConfig", "buildTypes", "buildFeatures", "compileOptions"
    )

    private val pythonJsKeywords = setOf(
        "def", "class", "import", "from", "as", "return", "if", "elif", "else",
        "for", "while", "in", "not", "and", "or", "is", "None", "True", "False",
        "try", "except", "finally", "raise", "with", "async", "await", "yield",
        "function", "const", "let", "var", "export", "default", "interface", "type",
        "extends", "implements", "switch", "case", "break", "continue", "new", "this"
    )

    fun highlight(code: String, extensionOrLang: String, isDark: Boolean): AnnotatedString {
        val palette = if (isDark) DarkPalette else LightPalette
        val lang = extensionOrLang.lowercase().trimStart('.')

        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = palette.plain), 0, code.length)

            val keywords = when (lang) {
                "py", "python", "js", "ts", "tsx", "jsx", "javascript", "typescript" -> pythonJsKeywords
                else -> kotlinJavaKeywords
            }

            if (lang == "xml" || lang == "html" || lang == "svg") {
                highlightXml(code, palette, this)
                return@buildAnnotatedString
            }

            if (lang == "json") {
                highlightJson(code, palette, this)
                return@buildAnnotatedString
            }

            // Keywords
            val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
            for (match in wordRegex.findAll(code)) {
                val word = match.value
                if (word in keywords) {
                    addStyle(
                        SpanStyle(color = palette.keyword, fontWeight = FontWeight.Medium),
                        match.range.first,
                        match.range.last + 1
                    )
                } else if (word.first().isUpperCase() && word.any { it.isLowerCase() }) {
                    addStyle(
                        SpanStyle(color = palette.type),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Numbers
            val numRegex = Regex("\\b0x[0-9A-Fa-f]+\\b|\\b\\d+(\\.\\d+)?[fFLu]?\\b")
            for (match in numRegex.findAll(code)) {
                addStyle(SpanStyle(color = palette.number), match.range.first, match.range.last + 1)
            }

            // Annotations (@Composable, @Entity, etc.)
            val annRegex = Regex("@[A-Za-z_][A-Za-z0-9_.]*")
            for (match in annRegex.findAll(code)) {
                addStyle(SpanStyle(color = palette.annotation), match.range.first, match.range.last + 1)
            }

            // Strings (double and single quoted)
            val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
            for (match in stringRegex.findAll(code)) {
                addStyle(SpanStyle(color = palette.string), match.range.first, match.range.last + 1)
            }

            // Comments (//, #, /* ... */)
            val blockComment = Regex("/\\*[\\s\\S]*?\\*/")
            for (match in blockComment.findAll(code)) {
                addStyle(
                    SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                    match.range.first,
                    match.range.last + 1
                )
            }
            val lineCommentRegex = if (lang in setOf("py", "python", "yml", "yaml", "sh", "bash", "toml", "properties")) {
                Regex("(?m)(//|#).*$")
            } else {
                Regex("(?m)//.*$")
            }
            for (match in lineCommentRegex.findAll(code)) {
                addStyle(
                    SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }

    private fun highlightXml(code: String, palette: SyntaxPalette, builder: AnnotatedString.Builder) {
        val tagRegex = Regex("</?[A-Za-z0-9_.:-]+|/?\\s*>")
        for (m in tagRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = palette.keyword), m.range.first, m.range.last + 1)
        }
        val attrRegex = Regex("\\s([A-Za-z0-9_.:-]+)=")
        for (m in attrRegex.findAll(code)) {
            val g = m.groups[1] ?: continue
            builder.addStyle(SpanStyle(color = palette.annotation), g.range.first, g.range.last + 1)
        }
        val strRegex = Regex("\"[^\"]*\"")
        for (m in strRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
        }
        val commentRegex = Regex("<!--([\\s\\S]*?)-->")
        for (m in commentRegex.findAll(code)) {
            builder.addStyle(
                SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                m.range.first,
                m.range.last + 1
            )
        }
    }

    private fun highlightJson(code: String, palette: SyntaxPalette, builder: AnnotatedString.Builder) {
        val strRegex = Regex("\"([^\"\\\\]|\\\\.)*\"")
        for (m in strRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = palette.string), m.range.first, m.range.last + 1)
        }
        val keyRegex = Regex("\"([^\"\\\\]|\\\\.)*\"\\s*:")
        for (m in keyRegex.findAll(code)) {
            val end = code.indexOf('"', m.range.first + 1) + 1
            if (end > m.range.first) {
                builder.addStyle(
                    SpanStyle(color = palette.type, fontWeight = FontWeight.Medium),
                    m.range.first,
                    end
                )
            }
        }
        val boolNullNum = Regex("\\b(true|false|null)\\b|-?\\d+(\\.\\d+)?")
        for (m in boolNullNum.findAll(code)) {
            builder.addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
        }
    }
}
