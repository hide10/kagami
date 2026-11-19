package com.kbcode.kagami

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** 日本語(values)と英語(values-en)が同じ文言の名前を持ち、差し込む引数も同じであることを確かめる。 */
class StringsTest {
    private class Entry(val text: String, val translatable: Boolean)

    /** テストはモジュール(app/)を作業ディレクトリにして走る。 */
    private fun read(folder: String): Map<String, Entry> {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue("$file がない", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate {
            it.getAttribute("name") to Entry(it.textContent, it.getAttribute("translatable") != "false")
        }
    }

    private val ja = read("values")
    private val en = read("values-en")

    @Test fun englishHasEveryJapaneseStringAndNoOthers() {
        val translatable = ja.filterValues { it.translatable }.keys
        assertTrue(translatable.size > 10)
        assertEquals(translatable.sorted(), en.keys.sorted())
    }

    @Test fun bothLanguagesTakeTheSameArguments() {
        val args = Regex("""%(\d+\$)?[-+#0 ,(]*\d*(\.\d+)?[a-zA-Z%]""")
        fun Entry.args() = args.findAll(text).map { it.value }.sorted().toList()
        for ((name, english) in en) assertEquals(name, ja.getValue(name).args(), english.args())
    }
}
