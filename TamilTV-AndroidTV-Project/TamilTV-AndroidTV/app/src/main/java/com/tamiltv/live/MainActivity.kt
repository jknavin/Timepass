package com.tamiltv.live

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.xml.sax.InputSource
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

private data class TvChannel(val name: String, val url: String, val logo: String, val group: String)

class MainActivity : Activity() {
    private val playlistUrl = "https://iptv-org.github.io/iptv/languages/tam.m3u"
    private val worker = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val channels = mutableListOf<TvChannel>()
    private val favorites = linkedSetOf<String>()
    private var filtered = listOf<TvChannel>()
    private var query = ""
    private var selectedGroup = "All channels"
    private var drawerOpen = false
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playerLayer: FrameLayout? = null
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var status: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var search: EditText
    private lateinit var menuButton: TextView
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        buildUi()
        loadPlaylist()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Float, stroke: Int = Color.TRANSPARENT): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius.toInt()).toFloat(); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }
    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(4, 5, 9)) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(26), dp(18), dp(26), dp(20)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("TAMIL TV", 28f, Color.WHITE, true))
        brand.addView(label("LIVE CHANNELS  •  MADE FOR YOUR TV", 12f, Color.rgb(166, 170, 190), true))
        top.addView(brand, LinearLayout.LayoutParams(0, dp(64), 1f))
        count = label("Loading channels…", 14f, Color.rgb(190, 180, 255), true)
        top.addView(count)
        menuButton = label("☰   MENU", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(18), dp(12), dp(18), dp(12)); background = bg(Color.rgb(32, 25, 52), 16f, Color.rgb(105, 82, 160)); isFocusable = true
            setOnClickListener { toggleDrawer() }
        }
        top.addView(menuButton, LinearLayout.LayoutParams(dp(142), dp(52)).apply { leftMargin = dp(20) })
        content.addView(top)
        status = label("Loading Tamil channel list from IPTV-org…", 15f, Color.LTGRAY)
        content.addView(status, LinearLayout.LayoutParams(-1, dp(44)))
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        grid = GridLayout(this).apply { columnCount = 4; alignmentMode = GridLayout.ALIGN_BOUNDS; useDefaultMargins = false }
        scroll.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(24), dp(22), dp(22)); background = bg(Color.rgb(15, 14, 24), 0f, Color.rgb(68, 58, 93)); elevation = dp(24).toFloat(); visibility = View.GONE
        }
        val drawerTitle = label("CHANNEL MENU", 20f, Color.WHITE, true)
        drawer.addView(drawerTitle, LinearLayout.LayoutParams(-1, dp(44)))
        search = EditText(this).apply {
            hint = "Search channels…"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); textSize = 16f; singleLine = true
            setPadding(dp(14), dp(8), dp(14), dp(8)); background = bg(Color.rgb(29, 28, 41), 12f, Color.rgb(73, 65, 100))
            addTextChangedListener(SimpleTextWatcher { query = it; renderGrid() })
        }
        drawer.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12); bottomMargin = dp(16) })
        addDrawerButton("◈   All channels") { selectedGroup = "All channels"; renderGrid(); toggleDrawer(false) }
        addDrawerButton("♥   Favourites") { selectedGroup = "Favourites"; renderGrid(); toggleDrawer(false) }
        addDrawerButton("↻   Refresh playlist") { toggleDrawer(false); loadPlaylist() }
        drawer.addView(label("CHANNEL GROUPS", 12f, Color.rgb(163, 145, 220), true), LinearLayout.LayoutParams(-1, dp(38)))
        val groupScroll = ScrollView(this)
        val groupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        groupScroll.addView(groupList)
        drawer.addView(groupScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        drawer.tag = groupList
        root.addView(drawer, FrameLayout.LayoutParams(dp(350), -1, Gravity.START))
        setContentView(root)
    }

    private fun addDrawerButton(text: String, action: () -> Unit) {
        val b = label(text, 16f, Color.WHITE, true).apply {
            setPadding(dp(14), 0, dp(12), 0); background = bg(Color.rgb(28, 26, 40), 12f); isFocusable = true; setOnClickListener { action() }
        }
        drawer.addView(b, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
    }

    private fun toggleDrawer(force: Boolean? = null) {
        drawerOpen = force ?: !drawerOpen
        drawer.visibility = if (drawerOpen) View.VISIBLE else View.GONE
        if (drawerOpen) {
            rebuildGroups()
            if (search.text.isNotEmpty()) search.setSelection(search.text.length)
            search.requestFocus()
        } else menuButton.requestFocus()
    }

    private fun rebuildGroups() {
        val groupList = drawer.tag as? LinearLayout ?: return
        groupList.removeAllViews()
        channels.map { it.group }.filter { it.isNotBlank() }.distinct().sorted().forEach { group ->
            val b = label("  $group", 15f, Color.rgb(222, 220, 238)).apply {
                setPadding(dp(10), 0, dp(8), 0); background = bg(Color.rgb(21, 20, 31), 10f); isFocusable = true
                setOnClickListener { selectedGroup = group; renderGrid(); toggleDrawer(false) }
            }
            groupList.addView(b, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(5) })
        }
    }

    private fun loadPlaylist() {
        status.text = "Loading channels…"
        worker.execute {
            try {
                val conn = URL(playlistUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 20000; conn.setRequestProperty("User-Agent", "TamilTV-AndroidTV/1.0")
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }; conn.disconnect()
                val parsed = parseM3u(text)
                mainHandler.post {
                    channels.clear(); channels.addAll(parsed)
                    count.text = "${channels.size} CHANNELS"
                    status.text = if (channels.isEmpty()) "No channels found. Check your internet and refresh." else "Choose a channel to start watching  •  Use MENU to search or browse groups"
                    rebuildGroups(); renderGrid()
                }
            } catch (e: Exception) {
                mainHandler.post { status.text = "Unable to load playlist. Connect your TV to the internet and select MENU → Refresh playlist."; count.text = "OFFLINE" }
            }
        }
    }

    private fun parseM3u(text: String): List<TvChannel> {
        val result = mutableListOf<TvChannel>(); var name = ""; var logo = ""; var group = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',', "Channel").trim().ifBlank { "Channel" }
                logo = attr(line, "tvg-logo").ifBlank { attr(line, "logo") }
                group = attr(line, "group-title")
            } else if (line.isNotBlank() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                result.add(TvChannel(name.ifBlank { "Tamil Channel ${result.size + 1}" }, line, logo, group.ifBlank { "Other" }))
                name = ""; logo = ""; group = ""
            }
        }
        return result
    }

    private fun attr(line: String, key: String): String {
        val regex = Regex("(?:^|\\s)${Regex.escape(key)}=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        grid.removeAllViews()
        val list = channels.filter { ch ->
            val matchesQuery = query.isBlank() || ch.name.contains(query, true) || ch.group.contains(query, true)
            val matchesGroup = when (selectedGroup) { "All channels" -> true; "Favourites" -> favorites.contains(ch.url); else -> ch.group == selectedGroup }
            matchesQuery && matchesGroup
        }
        filtered = list
        count.text = "${list.size} CHANNELS"
        if (list.isEmpty() && channels.isNotEmpty()) status.text = "No channels match this search or category."
        val width = resources.displayMetrics.widthPixels
        val cols = if (width >= dp(1500)) 5 else 4
        grid.columnCount = cols
        list.forEach { channel -> grid.addView(channelCard(channel), GridLayout.LayoutParams().apply {
            width = (width - dp(90)) / cols; height = dp(190); setMargins(dp(6), dp(7), dp(6), dp(7))
        }) }
    }

    private fun channelCard(channel: TvChannel): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(12), dp(12), dp(12), dp(10)); background = bg(Color.rgb(17, 17, 25), 18f, Color.rgb(52, 48, 69)); isFocusable = true; isClickable = true
            setOnFocusChangeListener { v, hasFocus -> v.background = bg(if (hasFocus) Color.rgb(40, 31, 61) else Color.rgb(17, 17, 25), 18f, if (hasFocus) Color.rgb(180, 150, 255) else Color.rgb(52, 48, 69)); if (hasFocus) v.scaleX = 1.03f else v.scaleX = 1f; v.scaleY = if (hasFocus) 1.03f else 1f }
            setOnClickListener { playChannel(channel) }
        }
        val logoBox = FrameLayout(this).apply { background = bg(Color.rgb(25, 24, 36), 14f); clipToOutline = true }
        val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        logoBox.addView(logo, FrameLayout.LayoutParams(-1, -1))
        val fallback = label("TV", 30f, Color.rgb(185, 157, 255), true).apply { gravity = Gravity.CENTER }
        logoBox.addView(fallback, FrameLayout.LayoutParams(-1, -1)); fallback.visibility = if (channel.logo.isBlank()) View.VISIBLE else View.GONE
        card.addView(logoBox, LinearLayout.LayoutParams(-1, 0, 1f))
        card.addView(label("● LIVE", 10f, Color.rgb(255, 94, 122), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(23)))
        card.addView(label(channel.name, 14f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, dp(38)))
        if (channel.logo.isNotBlank()) worker.execute {
            try {
                val c = URL(channel.logo).openConnection() as HttpURLConnection; c.connectTimeout = 7000; c.readTimeout = 7000
                val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }; c.disconnect()
                if (bmp != null) mainHandler.post { logo.setImageBitmap(bmp); fallback.visibility = View.GONE }
            } catch (_: Exception) { }
        }
        return card
    }

    private fun playChannel(channel: TvChannel) {
        closePlayer()
        val layer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); isFocusableInTouchMode = true }
        val pv = PlayerView(this).apply { useController = true; controllerAutoShow = true; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); keepScreenOn = true }
        layer.addView(pv, FrameLayout.LayoutParams(-1, -1))
        val info = label("${channel.name}  •  LIVE", 15f, Color.WHITE, true).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8)); background = bg(0xAA11111A.toInt(), 12f); elevation = dp(8).toFloat()
        }
        layer.addView(info, FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP or Gravity.START).apply { leftMargin = dp(20); topMargin = dp(20) })
        root.addView(layer, FrameLayout.LayoutParams(-1, -1)); playerLayer = layer; playerView = pv
        player = ExoPlayer.Builder(this).build().also { exo ->
            pv.player = exo
            exo.setMediaItem(MediaItem.fromUri(channel.url)); exo.prepare(); exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@MainActivity, "This stream may be offline or unsupported.", Toast.LENGTH_LONG).show()
                }
            })
        }
        pv.requestFocus()
    }

    private fun closePlayer() {
        playerView?.player = null; player?.release(); player = null; playerView = null
        playerLayer?.let { root.removeView(it) }; playerLayer = null
    }

    override fun onBackPressed() {
        if (playerLayer != null) { closePlayer(); return }
        if (drawerOpen) { toggleDrawer(false); return }
        super.onBackPressed()
    }

    override fun onDestroy() { closePlayer(); worker.shutdownNow(); super.onDestroy() }
}

private class SimpleTextWatcher(val onChange: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange(s?.toString().orEmpty()) }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
