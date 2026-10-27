package com.mufradati

import org.json.JSONArray
import org.json.JSONObject

object Exchange {
    val fields =
        listOf(
            "word",
            "meaning",
            "definition",
            "example",
            "synonyms",
            "part",
            "notes",
            "category",
            "favorite",
            "added",
        )

    private fun values(w: Word) =
        listOf(
            w.word,
            w.meaning,
            w.definition,
            w.example,
            w.synonyms,
            w.part,
            w.notes,
            w.category,
            w.favorite.toString(),
            w.added.toString(),
        )

    fun csv(words: List<Word>): String =
        (listOf(fields) + words.map(::values)).joinToString("\r\n") { row ->
            row.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        }

    fun json(words: List<Word>): String =
        JSONObject()
            .put("format", "mufradati-v1")
            .put(
                "words",
                JSONArray().apply {
                    words.forEach { w ->
                        put(
                            JSONObject().apply {
                                fields.zip(values(w)).forEach { (k, v) -> put(k, v) }
                            }
                        )
                    }
                },
            )
            .toString(2)

    fun parse(raw: String): List<Word> {
        val text = raw.removePrefix("\uFEFF").trim()
        val rows: List<Map<String, String>> =
            if (text.startsWith("{") || text.startsWith("[")) {
                val a =
                    if (text.startsWith("[")) JSONArray(text)
                    else JSONObject(text).getJSONArray("words")
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    fields.associateWith { o.optString(it, "") }
                }
            } else {
                val lines = parseCsv(text)
                require(lines.isNotEmpty())
                val h = lines.first().map { it.trim().lowercase() }
                require("word" in h && "meaning" in h)
                lines
                    .drop(1)
                    .filter { it.any(String::isNotBlank) }
                    .map { r ->
                        require(r.size == h.size)
                        h.zip(r).toMap()
                    }
            }
        require(rows.size <= 10000)
        return rows.map { r ->
            fun field(k: String) = r[k].orEmpty().trim()
            require(field("word").isNotEmpty() && field("meaning").isNotEmpty())
            require(r.values.all { it.length <= 10000 })
            Word(
                word = field("word"),
                meaning = field("meaning"),
                definition = field("definition"),
                example = field("example"),
                synonyms = field("synonyms"),
                part = field("part"),
                notes = field("notes"),
                category = field("category"),
                favorite = field("favorite").equals("true", true),
                added =
                    field("added").toLongOrNull()?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
        }
    }

    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                (c == '\n' || c == '\r') && !quoted -> {
                    row.add(cell.toString())
                    cell.clear()
                    rows.add(row)
                    row = mutableListOf()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted)
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }
}
