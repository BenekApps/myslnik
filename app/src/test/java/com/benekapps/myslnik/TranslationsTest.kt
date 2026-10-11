package com.benekapps.myslnik

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every Polish text must have a Ukrainian version with the same format
 * arguments (a missing %1$d crashes at runtime) and the same number of
 * array items (help numbers are matched by position).
 */
class TranslationsTest {
    private val res = File("src/main/res")

    private data class Resources(val strings: Map<String, String>, val arrays: Map<String, List<String>>)

    private fun load(dir: String, translatableOnly: Boolean): Resources {
        val strings = mutableMapOf<String, String>()
        val arrays = mutableMapOf<String, List<String>>()
        File(res, dir).listFiles { f -> f.extension == "xml" }!!.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            fun Element.translatable() = getAttribute("translatable") != "false"
            val s = doc.getElementsByTagName("string")
            for (i in 0 until s.length) {
                val e = s.item(i) as Element
                if (!translatableOnly || e.translatable()) strings[e.getAttribute("name")] = e.textContent
            }
            val a = doc.getElementsByTagName("string-array")
            for (i in 0 until a.length) {
                val e = a.item(i) as Element
                if (!translatableOnly || e.translatable()) {
                    val items = e.getElementsByTagName("item")
                    arrays[e.getAttribute("name")] = List(items.length) { items.item(it).textContent }
                }
            }
        }
        return Resources(strings, arrays)
    }

    private val formatArg = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfx]|%%")

    private fun args(text: String) = formatArg.findAll(text).map { it.value }.toList().sorted()

    @Test
    fun `ukrainian has every polish text with the same format arguments`() {
        val pl = load("values", translatableOnly = true)
        val uk = load("values-uk", translatableOnly = false)
        val missing = pl.strings.keys - uk.strings.keys
        assertTrue("Missing in values-uk: $missing", missing.isEmpty())
        pl.strings.forEach { (name, text) ->
            assertEquals("Format arguments differ in $name", args(text), args(uk.strings.getValue(name)))
        }
    }

    @Test
    fun `ukrainian arrays have the same number of items`() {
        val pl = load("values", translatableOnly = true)
        val uk = load("values-uk", translatableOnly = false)
        val missing = pl.arrays.keys - uk.arrays.keys
        assertTrue("Missing arrays in values-uk: $missing", missing.isEmpty())
        pl.arrays.forEach { (name, items) ->
            assertEquals("Item count differs in $name", items.size, uk.arrays.getValue(name).size)
        }
    }

    @Test
    fun `no ascii apostrophes in ukrainian texts`() {
        // An unescaped ' breaks Android string resources; use ’ instead.
        val uk = load("values-uk", translatableOnly = false)
        val bad = (uk.strings.values + uk.arrays.values.flatten()).filter { "'" in it }
        assertTrue("Use ’ instead of ': $bad", bad.isEmpty())
    }
}
