package com.example.data.device

import com.example.domain.model.UiNodeElement
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object UiAutomatorDumpParser {

    private val BOUNDS_REGEX = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    fun parse(xml: String): List<UiNodeElement> {
        val startIdx = xml.indexOf('<')
        if (startIdx < 0) return emptyList()
        val cleanXml = xml.substring(startIdx)

        val nodes = mutableListOf<UiNodeElement>()
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(cleanXml))

        var idx = 0
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                val text = parser.getAttributeValue(null, "text").orEmpty().trim()
                val contentDesc = parser.getAttributeValue(null, "content-desc").orEmpty().trim()
                val resourceId = parser.getAttributeValue(null, "resource-id").orEmpty().trim()
                val className = parser.getAttributeValue(null, "class").orEmpty().trim()
                val packageName = parser.getAttributeValue(null, "package").orEmpty().trim()
                val bounds = parser.getAttributeValue(null, "bounds").orEmpty().trim()
                val clickable = parser.getAttributeValue(null, "clickable") == "true"
                val scrollable = parser.getAttributeValue(null, "scrollable") == "true"
                val editable = parser.getAttributeValue(null, "editable") == "true" ||
                    className.endsWith("EditText")
                val focused = parser.getAttributeValue(null, "focused") == "true"

                val match = BOUNDS_REGEX.find(bounds)
                val centerX = if (match != null) {
                    val x1 = match.groupValues[1].toIntOrNull() ?: 0
                    val x2 = match.groupValues[3].toIntOrNull() ?: 0
                    (x1 + x2) / 2
                } else 0
                val centerY = if (match != null) {
                    val y1 = match.groupValues[2].toIntOrNull() ?: 0
                    val y2 = match.groupValues[4].toIntOrNull() ?: 0
                    (y1 + y2) / 2
                } else 0

                val hasMeaningfulInfo = text.isNotEmpty() ||
                    contentDesc.isNotEmpty() ||
                    clickable ||
                    scrollable ||
                    editable ||
                    focused

                val hasValidSize = match != null && run {
                    val x1 = match.groupValues[1].toIntOrNull() ?: 0
                    val y1 = match.groupValues[2].toIntOrNull() ?: 0
                    val x2 = match.groupValues[3].toIntOrNull() ?: 0
                    val y2 = match.groupValues[4].toIntOrNull() ?: 0
                    (x2 - x1) > 0 && (y2 - y1) > 0
                }

                if (hasMeaningfulInfo && hasValidSize) {
                    nodes += UiNodeElement(
                        index = idx++,
                        text = text,
                        contentDesc = contentDesc,
                        resourceId = resourceId,
                        className = className,
                        packageName = packageName,
                        bounds = bounds,
                        centerX = centerX,
                        centerY = centerY,
                        clickable = clickable,
                        scrollable = scrollable,
                        editable = editable,
                        focused = focused
                    )
                }
            }
            eventType = parser.next()
        }
        return nodes
    }

    fun formatForAgent(nodes: List<UiNodeElement>, maxNodes: Int = 80): String {
        if (nodes.isEmpty()) return "No interactive or labeled UI elements detected on screen."
        val shown = nodes.take(maxNodes)
        val sb = StringBuilder()
        sb.appendLine("Visible UI Elements (${nodes.size} total, showing ${shown.size}):")
        shown.forEach { n ->
            val flags = buildList {
                if (n.clickable) add("clickable")
                if (n.editable) add("editable")
                if (n.scrollable) add("scrollable")
                if (n.focused) add("focused")
            }.joinToString(",")
            val labelPart = buildList {
                if (n.text.isNotEmpty()) add("text=\"${n.text.take(60)}\"")
                if (n.contentDesc.isNotEmpty()) add("desc=\"${n.contentDesc.take(60)}\"")
                if (n.resourceId.isNotEmpty()) add("id=${n.resourceId.substringAfterLast('/')}")
            }.joinToString(" ")
            val pkg = n.packageName.takeIf { it.isNotEmpty() }?.let { " pkg=$it" }.orEmpty()
            sb.appendLine(
                "[${n.index}] tap=(${n.centerX},${n.centerY}) ${if (flags.isNotEmpty()) "[$flags] " else ""}$labelPart bounds=${n.bounds}$pkg"
            )
        }
        if (nodes.size > maxNodes) {
            sb.appendLine("... (${nodes.size - maxNodes} additional elements omitted)")
        }
        return sb.toString().trimEnd()
    }
}
