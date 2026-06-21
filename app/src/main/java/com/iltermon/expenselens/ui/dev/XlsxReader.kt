package com.iltermon.expenselens.ui.dev

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Minimal, dependency-free reader for `.xlsx` workbooks. Temporary — exists only to support the
 * one-time Excel migration (see ui/dev/DataImporter) and is removed afterward.
 *
 * Reads the user's spreadsheet straight from a Storage-Access-Framework [Uri] (no third-party
 * libs, no storage permission). Returns each sheet as a row-major, column-indexed grid of raw
 * string cell values; numeric cells (including Excel date serials) come back as their numeric
 * string and are interpreted by the importer.
 */
object XlsxReader {

    /** Map of sheet name → rows, each row a list of nullable cell values indexed by column. */
    fun read(context: Context, uri: Uri): Map<String, List<List<String?>>> {
        // ZipInputStream is sequential, so buffer the entries we care about in a single pass.
        val entries = HashMap<String, ByteArray>()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "xl/workbook.xml" ||
                        name == "xl/_rels/workbook.xml.rels" ||
                        name == "xl/sharedStrings.xml" ||
                        name.startsWith("xl/worksheets/")
                    ) {
                        entries[name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val relTargets = entries["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()
        val sheetDefs = entries["xl/workbook.xml"]?.let { parseWorkbook(it) } ?: emptyList()

        val result = LinkedHashMap<String, List<List<String?>>>()
        for ((sheetName, rId) in sheetDefs) {
            val target = relTargets[rId] ?: continue
            val path = if (target.startsWith("/")) target.substring(1) else "xl/$target"
            val bytes = entries[path] ?: continue
            result[sheetName] = parseSheet(bytes, sharedStrings)
        }
        return result
    }

    /** sharedStrings.xml → ordered list; the concatenated text of each `<si>`. */
    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val list = ArrayList<String>()
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inT) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> { inSi = false; list.add(sb.toString()) }
                }
            }
            event = parser.next()
        }
        return list
    }

    /** workbook.xml.rels → relationship Id → Target. */
    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val parser = newParser(bytes)
        val map = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (id != null && target != null) map[id] = target
            }
            event = parser.next()
        }
        return map
    }

    /** workbook.xml → ordered list of (sheet name, relationship id). */
    private fun parseWorkbook(bytes: ByteArray): List<Pair<String, String>> {
        val parser = newParser(bytes)
        val sheets = ArrayList<Pair<String, String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name")
                var rId: String? = null
                for (i in 0 until parser.attributeCount) {
                    val attr = parser.getAttributeName(i)
                    if (attr == "r:id" || attr.endsWith(":id") || attr == "id") {
                        rId = parser.getAttributeValue(i)
                    }
                }
                if (name != null && rId != null) sheets.add(name to rId)
            }
            event = parser.next()
        }
        return sheets
    }

    /** A worksheet's sheetData → rows of nullable, column-indexed cell values. */
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String?>> {
        val parser = newParser(bytes)
        val rows = ArrayList<List<String?>>()
        var row: ArrayList<String?>? = null
        var col = -1
        var cellType: String? = null
        val value = StringBuilder()
        var inV = false
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = ArrayList()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r")
                        col = if (ref != null) colIndex(ref) else (row?.size ?: 0)
                        cellType = parser.getAttributeValue(null, "t")
                        value.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inT = true // inline-string text (<is><t>)
                }
                XmlPullParser.TEXT -> if (inV || inT) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = value.toString()
                        val resolved =
                            if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1) else raw
                        val r = row ?: ArrayList<String?>().also { row = it }
                        while (r.size <= col) r.add(null)
                        r[col] = resolved?.takeIf { it.isNotEmpty() }
                    }
                    "row" -> { row?.let { rows.add(it) }; row = null }
                }
            }
            event = parser.next()
        }
        return rows
    }

    /** "AB12" → zero-based column index (AB → 27). */
    private fun colIndex(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch.isLetter()) col = col * 26 + (ch.uppercaseChar() - 'A' + 1) else break
        }
        return col - 1
    }

    private fun newParser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
}
