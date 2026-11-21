package com.chillspace.app.data

enum class PostType {
    REGULAR,
    STORY,
    POLL,
    DEBATE,
    AD,
    SCREEN_TIME
}

data class Comment(
    val author: String,
    val text: String,
    val avatarColor: String = "grey"
)

data class PollOption(
    val label: String,
    val votes: Int = 0,
    val percent: Int = 0
)

data class Post(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: PostType = PostType.REGULAR,
    val creator: String = "",
    val avatar: String = "grey",
    val minutesAgo: Int = 0,
    val content: String = "",
    val contentColor: String? = null,
    val likes: Int = 0,
    val userLiked: Boolean = false,
    val linesBIndex: Int? = null,
    val storyComments: List<Comment> = emptyList(),
    val pollOptions: List<PollOption> = emptyList(),
    val pollComments: List<Comment> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val debateCategory: String? = null,
    val adLabel: String? = null,
    val screenTimeData: List<Float> = emptyList(),
    val userVoted: Boolean = false
)

data class LangData(
    val isKorean: Boolean = false,
    var mainPhrase: String = "",
    var grammarStructure: String = "",
    var starters: List<String> = emptyList(),
    var topics: List<String> = emptyList(),
    var endings: List<String> = emptyList(),
    var linesB: List<String> = emptyList(),
    var firstNames: List<String> = emptyList(),
    var lastNames: List<String> = emptyList(),
    var politicians: List<String> = emptyList(),
    var politicalTopics: List<String> = emptyList(),
    var politicalJudgements: List<String> = emptyList(),
    var politicalBeneficiaries: List<String> = emptyList(),
    var algoResponses: Map<String, List<String>> = emptyMap(),
    var genericResponses: List<String> = emptyList()
)
