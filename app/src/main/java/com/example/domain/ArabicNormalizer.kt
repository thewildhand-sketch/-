package com.example.domain

object ArabicNormalizer {
    private val diacriticsRegex = Regex("[\u064B-\u065F\u0670\u0640]")
    private val multiSpaceRegex = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
            .replace(diacriticsRegex, "")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    fun tokenSimilarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        if (na.contains(nb) || nb.contains(na)) {
            val minLen = minOf(na.length, nb.length).toDouble()
            val maxLen = maxOf(na.length, nb.length).toDouble()
            return 0.72 + 0.25 * (minLen / maxLen)
        }
        val tokensA = na.split(" ").filter { it.isNotBlank() }.toSet()
        val tokensB = nb.split(" ").filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size.toDouble()
        val union = tokensA.union(tokensB).size.toDouble()
        return intersection / union
    }
}
