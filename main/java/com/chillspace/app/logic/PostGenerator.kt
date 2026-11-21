package com.chillspace.app.logic

import com.chillspace.app.data.Comment
import com.chillspace.app.data.LangData
import com.chillspace.app.data.PollOption
import com.chillspace.app.data.Post
import com.chillspace.app.data.PostType
import kotlin.random.Random

object PostGenerator {

    private val avatarColors = listOf("grey", "red", "green", "purple", "blue")
    private val colorHexes = listOf("#e11d48", "#7c3aed", "#059669", "#d97706", "#2563eb")

    /**
     * Fallback English linesB — used only when lang.xml provides no <linesb>
     * entries (or LangLoader failed entirely). Localised versions come from
     * lang.linesB, populated from <linesb><item>…</item></linesb>.
     */
    private val defaultLinesB = listOf(
        "Posted once today and didn't feel the need to check for likes. Revolutionary",
        "My screen time is down 80% since joining. My plants are thriving too",
        "Actually talked to a human IRL today instead of posting about it",
        "The algorithm shows me 3 posts and says 'go touch grass'. 10/10",
        "ChillSpace is the only app that doesn't leave me feeling empty inside. Weird flex but okay",
        "The notifications are so chill they're basically just gentle reminders that you're awesome",
        "My therapist asked what I do for self-care. I showed them ChillSpace. They approved.",
        "I used to spend hours on socials. Now I spend 10 minutes here and I'm actually... done?"
    )

    private val debateAdjectives = listOf(
        "overrated", "underrated", "amazing", "terrible", "iconic", "overhyped", "misunderstood"
    )

    private val debateSubjects = mapOf(
        "music" to listOf(
            "Taylor Swift", "BTS", "Drake", "Beyoncé", "Ed Sheeran",
            "Billie Eilish", "Dua Lipa", "Kendrick Lamar"
        ),
        "football" to listOf(
            "Manchester United", "Real Madrid", "Barcelona", "Liverpool",
            "Manchester City", "Bayern Munich", "Arsenal", "Chelsea"
        )
    )

    // ---------------------------------------------------------------
    //  Public entry points
    // ---------------------------------------------------------------

    fun generateInitialFeed(lang: LangData): List<Post> {
        val out = mutableListOf<Post>()
        out += generateAd()
        repeat(11) { i ->
            out += if (i == 4 && Random.nextFloat() < 0.3f) generateScreenTime(lang)
                   else generateRegular(lang)
        }
        return out
    }

    fun generateMore(count: Int, lang: LangData, startIndex: Int): List<Post> {
        return (0 until count).map {
            val r = Random.nextFloat()
            when {
                r < 0.05f -> generateScreenTime(lang)
                r < 0.25f && !lang.isKorean -> generateStory(lang)
                r < 0.40f && !lang.isKorean -> generatePoll(lang)
                r < 0.50f && !lang.isKorean -> generateDebate(lang)
                else -> generateRegular(lang)
            }
        }
    }

    // ---------------------------------------------------------------
    //  Individual generators
    // ---------------------------------------------------------------

    fun generateRegular(lang: LangData): Post {
        val (text, idx) = makeContent(lang)
        val color = if (Random.nextFloat() < 0.05f) colorHexes.random() else null
        return Post(
            type = PostType.REGULAR,
            creator = weightedName(lang),
            avatar = avatarColors.random(),
            minutesAgo = Random.nextInt(1, 60),
            content = text,
            contentColor = color,
            likes = Random.nextInt(0, 9999),
            linesBIndex = idx
        )
    }

    fun generateStory(lang: LangData): Post {
        val adjectives = listOf("craziest", "most insane", "funniest", "most shocking")
        val storyComments = (0 until Random.nextInt(8, 20)).map {
            Comment(
                author = "${lang.firstNames.randomOrNull() ?: "Alex"} " +
                         "${lang.lastNames.randomOrNull() ?: "Smith"}",
                avatarColor = avatarColors.random(),
                text = makeStoryComment()
            )
        }.toMutableList()
        return Post(
            type = PostType.STORY,
            creator = weightedName(lang),
            avatar = avatarColors.random(),
            minutesAgo = Random.nextInt(1, 60),
            content = "Guys, what are your ${adjectives.random()} stories?",
            likes = Random.nextInt(0, 9999),
            storyComments = storyComments
        )
    }

    fun generatePoll(lang: LangData): Post {
        val pool = lang.politicians.ifEmpty {
            listOf("Keir Starmer", "Rishi Sunak", "Emmanuel Macron", "Olaf Scholz", "Joe Biden")
        }
        val picked = pool.shuffled().take(Random.nextInt(2, 5))
        val rawVotes = picked.map { Random.nextInt(50, 900) }
        val total = rawVotes.sum().coerceAtLeast(1)
        val opts = picked.mapIndexed { idx, p ->
            PollOption(p, votes = rawVotes[idx], percent = rawVotes[idx] * 100 / total)
        }.toMutableList()

        val comments = (0 until Random.nextInt(2, 6)).map {
            Comment(
                author = "${lang.firstNames.randomOrNull() ?: "Alex"} " +
                         "${lang.lastNames.randomOrNull() ?: "Smith"}",
                avatarColor = avatarColors.random(),
                text = "Unpopular opinion: ${picked.random()}'s policies are " +
                       "${debateAdjectives.random()}."
            )
        }.toMutableList()

        return Post(
            type = PostType.POLL,
            creator = weightedName(lang),
            avatar = avatarColors.random(),
            minutesAgo = Random.nextInt(1, 60),
            content = "Who has the best policies right now?",
            likes = Random.nextInt(0, 3000),
            pollOptions = opts,
            pollComments = comments
        )
    }

    fun generateDebate(lang: LangData): Post {
        val cat = listOf("music", "football", "politics").random()
        val pool = if (cat == "politics")
            lang.politicians.ifEmpty { listOf("Keir Starmer", "Rishi Sunak", "Joe Biden") }
        else debateSubjects.getValue(cat)
        val a = pool.random()
        val b = pool.filter { it != a }.randomOrNull() ?: a
        val text = when (cat) {
            "football" -> "I think $a are ${debateAdjectives.random()} compared to $b in my opinion."
            "politics" -> "I think $a's policies are ${debateAdjectives.random()}."
            else       -> "I think that $a's music is ${debateAdjectives.random()}."
        }
        return Post(
            type = PostType.DEBATE,
            creator = weightedName(lang),
            avatar = avatarColors.random(),
            minutesAgo = 0,
            content = text,
            likes = Random.nextInt(0, 500),
            debateCategory = cat
        )
    }

    fun generateAd(): Post = Post(
        type = PostType.AD,
        creator = "ChillSpace",
        avatar = "blue",
        minutesAgo = 0,
        content = "Did you know that most social media apps use variable reward to keep you " +
                  "hooked, just like a slot machine? ChillSpace uses consistent reward — only " +
                  "showing you posts designed to make you happy.",
        likes = 0,
        adLabel = "Ad"
    )

    fun generateScreenTime(lang: LangData): Post {
        val data = (0 until 10).map {
            (6f - it * 0.55f + Random.nextFloat() * 0.8f).coerceAtLeast(0.3f)
        }
        return Post(
            type = PostType.SCREEN_TIME,
            creator = weightedName(lang),
            avatar = avatarColors.random(),
            minutesAgo = Random.nextInt(1, 60),
            content = "My screen time since downloading ChillSpace 📉",
            likes = Random.nextInt(0, 3000),
            screenTimeData = data
        )
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    fun weightedName(lang: LangData): String {
        if (lang.firstNames.isEmpty() || lang.lastNames.isEmpty()) {
            val f = listOf("Alex", "Jordan", "Casey", "Taylor", "Sam", "Riley").random()
            val l = listOf("Smith", "Chen", "Patel", "Kim", "Lee", "Garcia").random()
            return "$f $l"
        }
        return "${lang.firstNames.random()} ${lang.lastNames.random()}"
    }

    /**
     * Content generator.
     *
     * Order of preference for non-question posts:
     *   1. linesB (localised via lang.linesB, English fallback via defaultLinesB)
     *   2. Question template (English-only phrasing, matches AiResponder's p2)
     *   3. Structured opinion post, using lang.mainPhrase + lang.grammarStructure
     *      so Korean gets its own word order and mainphrase.
     */
    fun makeContent(lang: LangData): Pair<String, Int?> {
        // ---- 1. linesB (LANGUAGE-AWARE) ----
        val linesB = if (lang.linesB.isNotEmpty()) lang.linesB else defaultLinesB
        val r = Random.nextFloat()
        if (r < 0.65f) {
            val idx = Random.nextInt(0, linesB.size)
            return linesB[idx] to idx
        }

        // ---- 2. Question template ----
        if (r < 0.80f) {
            val places = listOf("Japan", "Brazil", "Korea", "Europe", "the UK")
            val things = listOf(
                "bubble tea", "pickleball", "K-drama", "matcha", "vinyl collecting"
            )
            return "Why is ${things.random()} so popular in ${places.random()} right now?" to null
        }

        // ---- 3. Structured opinion post (mainphrase + grammarstructure) ----
        val op = lang.starters.randomOrNull() ?: "I love"
        val tA = lang.topics.randomOrNull() ?: "cats"
        val tB = lang.endings.randomOrNull() ?: "travel"
        val mp = lang.mainPhrase

        val content = if (lang.grammarStructure == "topicA+opinionA+topicB+mainPhrase") {
            // Topic-first word order (Korean, Japanese, etc.)
            "$tA $op. $tB $mp".trim()
        } else {
            // Default: opinionA+topicA+mainPhrase+topicB (English word order)
            "$op $tA $mp $tB.".trim()
        }
        return content to null
    }

    fun makeStoryComment(): String {
        val verbs = listOf(
            "broke up with", "got with", "had an argument with", "fell in love with"
        )
        val rels = listOf(
            "wife", "husband", "boyfriend", "girlfriend", "best friend", "crush"
        )
        val topics = listOf(
            "painting", "music", "a video game", "a trip to Japan", "fast food"
        )
        return "I ${verbs.random()} my ${rels.random()} over ${topics.random()}."
    }
}