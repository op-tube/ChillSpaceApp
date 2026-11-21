package com.chillspace.app.data

import android.content.Context
import org.w3c.dom.Element
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object LangLoader {

    fun load(context: Context): LangData {
        val locale = Locale.getDefault().language.lowercase()
        val tag = when {
            locale.startsWith("ko") -> "korean"
            locale.startsWith("es") -> "spanish"
            locale.startsWith("ja") -> "japanese"
            else -> "english"
        }

        return try {
            context.assets.open("lang.xml").use { input ->
                parse(input, tag)
            }
        } catch (e: Exception) {
            LangData()   // fallback to defaults
        }
    }

    private fun parse(input: InputStream, tag: String): LangData {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(input)

        val langNode: Element? = when {
            doc.getElementsByTagName(tag).length > 0 ->
                doc.getElementsByTagName(tag).item(0) as Element
            doc.getElementsByTagName("english").length > 0 ->
                doc.getElementsByTagName("english").item(0) as Element
            else -> null
        }
        val node: Element = langNode ?: return LangData()

        val out = LangData(isKorean = tag == "korean")

        // ---- <content> : attributes + children ----
        val contentNodes = node.getElementsByTagName("content")
        if (contentNodes.length > 0) {
            val content = contentNodes.item(0) as Element

            // === THE ACTUAL FIX === attributes, not children
            content.getAttribute("mainphrase").takeIf { it.isNotBlank() }
                ?.let { out.mainPhrase = it.trim() }
            content.getAttribute("grammarstructure").takeIf { it.isNotBlank() }
                ?.let { out.grammarStructure = it.trim() }

            out.starters = textOf(content, "contentopinions").splitCsv()
            out.topics = textOf(content, "topica").splitCsv()
            out.endings = textOf(content, "topicb").splitCsv()
        }

        // ---- linesB ----
        val linesbNode = node.getElementsByTagName("linesb")
        if (linesbNode.length > 0) {
            val items = (linesbNode.item(0) as Element).getElementsByTagName("item")
            val list = mutableListOf<String>()
            for (i in 0 until items.length) list += items.item(i).textContent.trim()
            out.linesB = list
        }

        // ---- names ----
        val nameNodes = node.getElementsByTagName("names")
        if (nameNodes.length > 0) {
            val n = nameNodes.item(0) as Element
            out.firstNames = textOf(n, "first").splitCsv()
            out.lastNames = textOf(n, "last").splitCsv()
        }

        // ---- debates ----
        val debateNodes = node.getElementsByTagName("debateposts")
        if (debateNodes.length > 0) {
            val d = debateNodes.item(0) as Element
            out.politicians = textOf(d, "politicians").splitCsv()
            out.politicalTopics = textOf(d, "politicaltopics").splitCsv()
            out.politicalJudgements = textOf(d, "politicaljudgements").splitCsv()
            out.politicalBeneficiaries = textOf(d, "politicalbeneficiaries").splitCsv()
        }

        // ---- algo responses ----
        val algoNodes = node.getElementsByTagName("algo-responses")
        if (algoNodes.length > 0) {
            val root = algoNodes.item(0) as Element
            val cats = root.getElementsByTagName("category")
            val map = mutableMapOf<String, List<String>>()
            for (i in 0 until cats.length) {
                val c = cats.item(i) as Element
                val name = c.getAttribute("name")
                if (name.isNotBlank()) map[name] = c.textContent.splitCsv()
            }
            out.algoResponses = map
        }

        // ---- generic responses ----
        val genericNodes = node.getElementsByTagName("generic-responses")
        if (genericNodes.length > 0) {
            out.genericResponses = (genericNodes.item(0) as Element).textContent.splitCsv()
        }

        return out
    }

    private fun textOf(parent: Element, tag: String): String {
        val list = parent.getElementsByTagName(tag)
        return if (list.length > 0) list.item(0).textContent else ""
    }

    private fun String.splitCsv(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
