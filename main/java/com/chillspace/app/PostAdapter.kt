package com.chillspace.app

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chillspace.app.data.Comment
import com.chillspace.app.data.PollOption
import com.chillspace.app.data.Post
import com.chillspace.app.data.PostType
import com.google.android.material.button.MaterialButton

class PostAdapter(
    private val posts: MutableList<Post>,
    private val onLike: (Post) -> Unit,
    private val onComment: (Post) -> Unit,
    private val onAi: (Post) -> Unit,
    private val onRepost: (Post) -> Unit,
    private val onVote: (Post, Int) -> Unit,
    private val onExpandStory: (Post, Int) -> Unit,
    private val onExpandPoll: (Post, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_POLL = 1
    }

    // in-memory expansion state — dies with the activity (fine, "always fresh")
    private val expandedStories = mutableSetOf<String>()
    private val expandedPolls   = mutableSetOf<String>()

    fun toggleStoryExpansion(position: Int) {
        val post = posts.getOrNull(position) ?: return
        if (expandedStories.contains(post.id)) expandedStories.remove(post.id)
        else expandedStories.add(post.id)
        notifyItemChanged(position)
    }

    fun togglePollExpansion(position: Int) {
        val post = posts.getOrNull(position) ?: return
        if (expandedPolls.contains(post.id)) expandedPolls.remove(post.id)
        else expandedPolls.add(post.id)
        notifyItemChanged(position)
    }

    override fun getItemViewType(position: Int): Int =
        if (posts[position].type == PostType.POLL) TYPE_POLL else TYPE_POST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_POLL)
            PollViewHolder(inflater.inflate(R.layout.item_poll, parent, false))
        else
            PostViewHolder(inflater.inflate(R.layout.item_post, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val post = posts[position]
        when (holder) {
            is PostViewHolder -> holder.bind(post, position)
            is PollViewHolder -> holder.bind(post, position)
        }
    }

    override fun getItemCount() = posts.size

    // ---- common helpers ----
    private fun avatarColor(name: String): Int {
        val palette = intArrayOf(
            0xFF6B7280.toInt(), 0xFFEF4444.toInt(), 0xFF10B981.toInt(),
            0xFF8B5CF6.toInt(), 0xFF1877F2.toInt()
        )
        val idx = (name.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[idx]
    }

    private fun applyColored(textView: TextView, text: String, colorHex: String?) {
        if (colorHex == null) {
            textView.text = text
            return
        }
        try {
            val color = Color.parseColor(colorHex)
            val spannable = SpannableString(text)
            spannable.setSpan(
                ForegroundColorSpan(color), 0, spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            textView.text = spannable
        } catch (_: Exception) { textView.text = text }
    }

    // ======================= PostViewHolder =======================
    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView           = view.findViewById(R.id.postAvatar)
        val name: TextView             = view.findViewById(R.id.postName)
        val badge: TextView            = view.findViewById(R.id.postBadge)
        val time: TextView             = view.findViewById(R.id.postTime)
        val content: TextView          = view.findViewById(R.id.postContent)
        val graph: ScreenTimeGraphView = view.findViewById(R.id.screenTimeGraph)
        val btnLike: MaterialButton    = view.findViewById(R.id.btnLike)
        val btnComment: MaterialButton = view.findViewById(R.id.btnComment)
        val btnRepost: MaterialButton  = view.findViewById(R.id.btnRepost)
        val btnAi: MaterialButton      = view.findViewById(R.id.btnAi)
        val btnStoryComments: MaterialButton = view.findViewById(R.id.btnShowStoryComments)
        val storyContainer: LinearLayout     = view.findViewById(R.id.storyCommentsContainer)

        fun bind(post: Post, position: Int) {
            avatar.text = post.creator.firstOrNull()?.uppercase() ?: "?"
            avatar.setBackgroundColor(avatarColor(post.creator))
            name.text = post.creator
            time.text = when (post.type) {
                PostType.STORY -> "${post.minutesAgo}m ago · Story"
                PostType.DEBATE -> "Just now · 🌐"
                else -> "${post.minutesAgo}m ago · 🌐"
            }

            when (post.type) {
                PostType.AD -> { badge.visibility = View.VISIBLE; badge.text = "Ad" }
                PostType.DEBATE -> { badge.visibility = View.VISIBLE; badge.text = "● LIVE" }
                else -> badge.visibility = View.GONE
            }

            applyColored(content, post.content, post.contentColor)

            // screen-time graph
            if (post.type == PostType.SCREEN_TIME && post.screenTimeData.isNotEmpty()) {
                graph.visibility = View.VISIBLE
                graph.setData(post.screenTimeData)
            } else {
                graph.visibility = View.GONE
            }

            // story comments
            if (post.type == PostType.STORY && post.storyComments.isNotEmpty()) {
                btnStoryComments.visibility = View.VISIBLE
                val expanded = expandedStories.contains(post.id)
                btnStoryComments.text = if (expanded)
                    "🔼 Hide comments" else "💬 Show comments (${post.storyComments.size})"
                storyContainer.visibility = if (expanded) View.VISIBLE else View.GONE
                if (expanded) renderCommentList(storyContainer, post.storyComments)
                btnStoryComments.setOnClickListener { onExpandStory(post, position) }
            } else {
                btnStoryComments.visibility = View.GONE
                storyContainer.visibility = View.GONE
            }

            // actions
            btnLike.text = if (post.userLiked) "✅ ${post.likes}" else "👍 ${post.likes}"
            btnLike.setOnClickListener { onLike(post) }
            btnComment.setOnClickListener { onComment(post) }
            btnComment.visibility = if (post.type == PostType.AD) View.GONE else View.VISIBLE
            btnRepost.setOnClickListener { onRepost(post) }
            btnAi.setOnClickListener { onAi(post) }
        }
    }

    // ======================= PollViewHolder =======================
    inner class PollViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView            = view.findViewById(R.id.pollAvatar)
        val name: TextView              = view.findViewById(R.id.pollName)
        val time: TextView              = view.findViewById(R.id.pollTime)
        val question: TextView          = view.findViewById(R.id.pollQuestion)
        val optionsContainer: LinearLayout = view.findViewById(R.id.pollOptionsContainer)
        val total: TextView             = view.findViewById(R.id.pollTotal)
        val btnLike: MaterialButton     = view.findViewById(R.id.pollBtnLike)
        val btnRepost: MaterialButton   = view.findViewById(R.id.pollBtnRepost)
        val btnAi: MaterialButton       = view.findViewById(R.id.pollBtnAi)
        val btnComments: MaterialButton = view.findViewById(R.id.pollBtnComments)
        val commentsContainer: LinearLayout = view.findViewById(R.id.pollCommentsContainer)

        fun bind(post: Post, position: Int) {
            avatar.text = post.creator.firstOrNull()?.uppercase() ?: "?"
            avatar.setBackgroundColor(avatarColor(post.creator))
            name.text = post.creator
            time.text = "${post.minutesAgo}m ago · 🌐"
            question.text = post.content

            optionsContainer.removeAllViews()
            post.pollOptions.forEachIndexed { idx, option ->
                optionsContainer.addView(buildPollOptionRow(option, post.userVoted) { 
                    onVote(post, idx) 
                })
            }

            if (post.userVoted) {
                val totalVotes = post.pollOptions.sumOf { it.votes }
                total.visibility = View.VISIBLE
                total.text = "${totalVotes} votes"
            } else {
                total.visibility = View.GONE
            }

            btnLike.text = if (post.userLiked) "✅ ${post.likes}" else "👍 ${post.likes}"
            btnLike.setOnClickListener { onLike(post) }
            btnRepost.setOnClickListener { onRepost(post) }
            btnAi.setOnClickListener { onAi(post) }

            val expanded = expandedPolls.contains(post.id)
            val commentCount = post.pollComments.size
            btnComments.text = if (expanded) "🔼 Hide comments"
                               else "💬 Show comments ($commentCount)"
            commentsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
            if (expanded) renderCommentList(commentsContainer, post.pollComments)
            btnComments.setOnClickListener { onExpandPoll(post, position) }
            btnComments.visibility = if (commentCount > 0) View.VISIBLE else View.GONE
        }

        private fun buildPollOptionRow(
            option: PollOption, voted: Boolean, onClick: () -> Unit
        ): View {
            val btn = MaterialButton(itemView.context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }
                text = if (voted) "${option.label}  ·  ${option.percent}%" else option.label
                isAllCaps = false
                textSize = 13f
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                isEnabled = !voted
                isFocusable = true
            }
            if (!voted) btn.setOnClickListener { onClick() }
            return btn
        }
    }

    // ---- shared comment renderer ----
    private fun renderCommentList(container: LinearLayout, comments: List<Comment>) {
        container.removeAllViews()
        comments.forEach { c ->
            val tv = TextView(container.context).apply {
                text = "${c.author}: ${c.text}"
                textSize = 13f
                setPadding(0, 4, 0, 4)
                setTextColor(0xFF1C1E21.toInt())
            }
            container.addView(tv)
        }
    }
}