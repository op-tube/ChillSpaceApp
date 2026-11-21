package com.chillspace.app.logic

object AiResponder {

    private val p1 = Regex("""^(.+?)\s+which makes sense because I'?ve always loved (.+?)\.?$""", RegexOption.IGNORE_CASE)
    private val p2 = Regex("""^(how|why)\s+is\s+(.+?)\s+(so\s+(?:underrated|popular))\s+in\s+(.+?)\??$""", RegexOption.IGNORE_CASE)
    private val nonLatin = Regex("""[\u0080-\u024F\u0370-\u03FF\u0400-\u04FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u1100-\u11FF\u3000-\u9FFF\uAC00-\uD7AF]""")

    fun respond(text: String): String {
        val m1 = p1.find(text)
        if (m1 != null) {
            val activity = m1.groupValues[1].trim()
            val thing = m1.groupValues[2].trim().trimEnd('.')
            val swapped = activity
                .replace(Regex("""\bI'm\b""", RegexOption.IGNORE_CASE), "they're")
                .replace(Regex("""\bI've\b""", RegexOption.IGNORE_CASE), "they've")
                .replace(Regex("""\bI'd\b""", RegexOption.IGNORE_CASE), "they'd")
                .replace(Regex("""\bI'll\b""", RegexOption.IGNORE_CASE), "they'll")
                .replace(Regex("""\bI\b"""), "they")
                .replace(Regex("""\bmy\b""", RegexOption.IGNORE_CASE), "their")
                .replace(Regex("""\bmyself\b""", RegexOption.IGNORE_CASE), "themselves")
            return "This user seems to like $thing, so $swapped."
        }
        val m2 = p2.find(text)
        if (m2 != null) {
            val thing = m2.groupValues[2].trim()
            val descriptor = m2.groupValues[3].trim()
            val place = m2.groupValues[4].trim().trimEnd('?')
            val cap = thing.replaceFirstChar { it.uppercase() }
            return "$cap is $descriptor in $place due to societal factors and personal preferences."
        }
        if (nonLatin.containsMatchIn(text)) {
            return "Sorry, our AI can currently only process English text. Try again with a different post."
        }
        return "I don't yet know how to analyse this post. You could try asking a third-party AI or using your brain to interpret the post yourself."
    }
}