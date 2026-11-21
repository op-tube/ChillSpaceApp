package com.chillspace.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chillspace.app.data.Comment
import com.chillspace.app.data.LangData
import com.chillspace.app.data.LangLoader
import com.chillspace.app.data.Post
import com.chillspace.app.data.PostType
import com.chillspace.app.logic.AiResponder
import com.chillspace.app.logic.PostGenerator
import com.google.android.material.button.MaterialButton

class LiteFeedActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: PostAdapter
    private lateinit var btnAutoScroll: MaterialButton
    private lateinit var lang: LangData

    private val posts = mutableListOf<Post>()
    private var loadingMore = false
    private var isAutoScrolling = false

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val autoScrollTick = object : Runnable {
        override fun run() {
            if (!isAutoScrolling) return
            recycler.scrollBy(0, 6)
            autoScrollHandler.postDelayed(this, 80)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lite_feed)

        lang = LangLoader.load(this)

        recycler = findViewById(R.id.recyclerPosts)
        btnAutoScroll = findViewById(R.id.btnAutoScroll)

        adapter = PostAdapter(
            posts,
            onLike = ::toggleLike,
            onComment = ::openCommentDialog,
            onAi = ::showAiResponse,
            onRepost = ::repostPost,
            onVote = ::votePoll,
            onExpandStory = ::toggleStoryComments,
            onExpandPoll = ::togglePollComments
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0 || loadingMore) return
                val lm = rv.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= posts.size - 3) {
                    loadMore()
                }
            }
        })

        btnAutoScroll.setOnClickListener { toggleAutoScroll() }

        loadInitial()
    }

    override fun onDestroy() {
        super.onDestroy()
        isAutoScrolling = false
        autoScrollHandler.removeCallbacks(autoScrollTick)
    }

    private fun loadInitial() {
        posts.addAll(PostGenerator.generateInitialFeed(lang))
        adapter.notifyDataSetChanged()
    }

    private fun loadMore() {
        if (loadingMore) return
        loadingMore = true
        val start = posts.size
        val more = PostGenerator.generateMore(8, lang, start)
        posts.addAll(more)
        adapter.notifyItemRangeInserted(start, more.size)
        loadingMore = false
    }

    // ---- actions ----

    private fun toggleLike(post: Post) {
        val idx = posts.indexOfFirst { it.id == post.id }
        if (idx < 0) return
        val cur = posts[idx]
        posts[idx] = cur.copy(likes = if (cur.userLiked) cur.likes - 1 else cur.likes + 1,
                              userLiked = !cur.userLiked)
        adapter.notifyItemChanged(idx)
    }

    private fun showAiResponse(post: Post) {
        Toast.makeText(this, AiResponder.respond(post.content), Toast.LENGTH_LONG).show()
    }

    private fun repostPost(post: Post) {
        val url = "https://chillspace.ai/?post_creator=${UriEncoder(post.creator)}" +
                  "&post_content=${UriEncoder(post.content)}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, "Share post"))
    }

    private fun UriEncoder(s: String): String = android.net.Uri.encode(s)

    private fun votePoll(post: Post, optionIdx: Int) {
        val idx = posts.indexOfFirst { it.id == post.id }
        if (idx < 0) return
        val cur = posts[idx]
        if (cur.userVoted) return
        val newOptions = cur.pollOptions.toMutableList()
        val target = newOptions[optionIdx]
        newOptions[optionIdx] = target.copy(votes = target.votes + 1)
        val total = newOptions.sumOf { it.votes }
        val repct = newOptions.map { o -> o.copy(percent = if (total == 0) 0 else o.votes * 100 / total) }
        posts[idx] = cur.copy(pollOptions = repct, userVoted = true)
        adapter.notifyItemChanged(idx)
    }

    private fun toggleStoryComments(post: Post, position: Int) {
        adapter.toggleStoryExpansion(position)
    }

    private fun togglePollComments(post: Post, position: Int) {
        adapter.togglePollExpansion(position)
    }

    private fun openCommentDialog(post: Post) {
        val idx = posts.indexOfFirst { it.id == post.id }
        if (idx < 0) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }
        val input = EditText(this).apply {
            hint = "Say something nice..."
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        container.addView(input)

        if (post.comments.isNotEmpty()) {
            val header = TextView(this).apply {
                text = "Existing comments:"
                textSize = 12f
                setPadding(0, 24, 0, 8)
            }
            container.addView(header)
            post.comments.forEach { c ->
                container.addView(TextView(this).apply {
                    text = "${c.author}: ${c.text}"
                    textSize = 13f
                    setPadding(0, 4, 0, 4)
                })
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Comment")
            .setView(container)
            .setPositiveButton("Post") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val newComment = Comment(author = "You", text = text, avatarColor = "blue")
                    posts[idx] = posts[idx].copy(
                        comments = (posts[idx].comments + newComment).toMutableList()
                    )
                    adapter.notifyItemChanged(idx)
                    Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleAutoScroll() {
        isAutoScrolling = !isAutoScrolling
        if (isAutoScrolling) {
            btnAutoScroll.text = "🤖 Auto: On"
            btnAutoScroll.setBackgroundColor(0xFF7C3AED.toInt())
            btnAutoScroll.setTextColor(0xFFFFFFFF.toInt())
            autoScrollHandler.post(autoScrollTick)
        } else {
            btnAutoScroll.text = "🤖 Auto: Off"
            btnAutoScroll.setBackgroundColor(0xFFE4E6EB.toInt())
            btnAutoScroll.setTextColor(0xFF1C1E21.toInt())
            autoScrollHandler.removeCallbacks(autoScrollTick)
        }
    }
}