package com.example.aitoui.data

import kotlin.math.max

/**
 * Text-similarity helpers for fuzzy-matching entered medication / dispensable-unit values against existing
 * records. Pure and JVM-testable.
 */
object TextSimilarity {

    /** Lowercase, replace punctuation with spaces, collapse whitespace, trim — for tolerant comparison. */
    fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Similarity of two strings in 0..1 (1 = identical after [normalize]), via Levenshtein distance. */
    fun ratio(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        val maxLen = max(x.length, y.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(x, y).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}
