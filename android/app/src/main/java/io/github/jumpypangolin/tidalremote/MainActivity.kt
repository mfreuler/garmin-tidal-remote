package io.github.jumpypangolin.tidalremote

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Settings screen: phone setup, watch lists, Tidal API login, options, and the command log. */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_HIDE_NOTIFICATION = "hideNotification"
        private val ACCENT = Color.rgb(0, 170, 170)
        private val MUTED = Color.rgb(160, 160, 160)
        private val WARNING = Color.rgb(230, 120, 80)
        private val CARD = Color.rgb(38, 38, 42)
        private const val LOG_LINES = 10
        private const val DISCLAIMER = "Tidal Remote is an independent, non-commercial hobby project. " +
            "It is not affiliated with, endorsed or sponsored by TIDAL or Garmin. " +
            "TIDAL and Garmin are trademarks of their respective owners."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var config: Config
    private lateinit var root: LinearLayout

    /** Where add() puts views: the page, or the card of the current section. */
    private lateinit var container: LinearLayout
    private lateinit var genreSummary: TextView
    private var availableGenres: List<String> = emptyList()
    private var chosenGenres: List<String> = emptyList()
    private lateinit var setupSection: Section
    private lateinit var genresSection: Section
    private lateinit var searchSection: Section
    private lateinit var radioSection: Section
    private lateinit var otherSection: Section
    private lateinit var logMore: Button
    private var showAllLog = false
    private lateinit var terms: EditText
    private lateinit var clientId: EditText
    private lateinit var clientSecret: EditText
    private lateinit var country: EditText
    private lateinit var autoShuffle: CheckBox
    private lateinit var setupStatus: TextView
    private lateinit var genreInfo: TextView
    private lateinit var log: TextView
    private lateinit var gettingStarted: LinearLayout
    private var tidalFailed = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = Config(this)
        val pad = dp(16)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        container = root

        // Spacing, everywhere: 8 dp from a heading or between related items, 4 dp from a label to its
        // field or text, 16 dp between groups inside a card, 12 dp between cards.
        add(TextView(this).apply {
            text = "Tidal Remote"
            textSize = 24f
        }, top = 0)
        add(help("This is the companion app for the \"Garmin-Tidal Remote\" app for Garmin watches on GitHub. " +
            "Configure settings below to control Tidal from your watch. " +
            "Saved changes will be reflected after the next start of the watch app."), top = 8)

        // Shown only while Tidal refuses: the first-time steps Android and Tidal don't make obvious.
        gettingStarted = section("Getting started").card
        add(help("Tidal Remote can't reach Tidal yet. Do these steps once:"), top = 0)
        add(help("1. Turn on notification access for Tidal Remote. If Android says \"Restricted setting\", " +
            "open App info, tap ⋮ (top right) → Allow restricted settings, then try again."), top = 16)
        add(button("Notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, top = 8)
        add(button("App info: Tidal Remote") { openAppInfo(packageName) }, top = 8)
        add(help("2. Force-stop Tidal once, so it accepts Tidal Remote: App info → Force stop. " +
            "Android doesn't let other apps do this."), top = 16)
        add(button("App info: Tidal") { openAppInfo(TidalController.TIDAL) }, top = 8)
        add(help("3. Come back here. The genres load by themselves and this box disappears."), top = 16)
        add(button("Try again") { loadFromTidal() }, top = 8)
        gettingStarted.visibility = View.GONE

        // Open while something is missing; once all is set it folds away to one line.
        setupSection = section("1. Phone setup", collapsible = true)
        add(help("Notification access is mandatory for Tidal to accept commands. Battery exception is strongly recommended " +
            "for Tidal Remote and Tidal to avoid time-out issues after a few hours of inactivity."), top = 0)
        add(button("Allow access to Tidal notifications") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, top = 8)
        add(button("Allow Tidal Remote unrestricted (battery)") { requestBatteryExemption() }, top = 8)
        // Android only lets an app request this for itself, so for Tidal open its App info page.
        add(button("Allow Tidal unrestricted (battery)") { openAppInfo(TidalController.TIDAL) }, top = 8)
        setupStatus = help("").also { add(it, top = 8) }
        setupSection.open = !updateSetupStatus()

        // Same order and names as the watch menu.
        genresSection = section("2. Genres", key = "open_genres", defaultOpen = true)
        add(help("The genres on the watch. The watch plays a random playlist from the chosen genre. " +
            "My Mix and Favorites need no setup: they come from Tidal."), top = 0)
        genreSummary = TextView(this).apply { textSize = 15f }.also { add(it, top = 8) }
        add(button("Edit genres") { editGenres() }, top = 8)
        genreInfo = help("").also {
            it.visibility = View.GONE
            add(it, top = 8)
        }
        showGenres(config.cachedGenres, config.genres)
        loadFromTidal()

        searchSection = section("3. Search", key = "open_search", defaultOpen = true)
        add(help("Search terms, one per line, any words. The watch plays a random playlist from Tidal's search results."), top = 0)
        terms = multiline(config.searchTerms).also { add(it, top = 8) }

        radioSection = section("4. Track radio", key = "open_radio", defaultOpen = false)
        add(help("Optional, only needed for Track radio. Sign in at developer.tidal.com with your Tidal account, " +
            "create an app, and copy its Client ID and Client secret here."), top = 0)
        add(label("Client ID"), top = 16)
        clientId = field("Client ID", config.clientId, false).also { add(it, top = 4) }
        add(label("Client secret"), top = 16)
        clientSecret = field("Client secret", config.clientSecret, true).also { add(it, top = 4) }
        add(label("Country of your Tidal account"), top = 16)
        add(help("The two-letter code of the country where you signed up for Tidal, for example CH (Switzerland), " +
            "DE (Germany), GB (United Kingdom), BR (Brazil), US (USA). Pre-filled from your SIM card. " +
            "Track radio looks up its songs for this country."), top = 4)
        country = field("CH", config.country, false).also { add(it, top = 8) }

        otherSection = section("Other settings", key = "open_options", defaultOpen = false)
        add(label("Permanent notification"), top = 0)
        add(help("Android shows a notification while the app listens for the watch. You can hide it, but some phones " +
            "may stop the app in the background without it. If the watch can't reach the phone, show it again."), top = 4)
        add(button("Hide or show the notification") { startActivity(WatchService.channelSettings(this)) }, top = 8)
        add(label("Auto-shuffle"), top = 16)
        add(help("Switches shuffle on whenever the watch starts a new playlist."), top = 4)
        autoShuffle = CheckBox(this).apply {
            text = "Auto-shuffle new playlists"
            isChecked = config.autoShuffle
            // The default tint is black on this dark screen, which hides the tick.
            buttonTintList = ColorStateList.valueOf(ACCENT)
        }
        add(autoShuffle, top = 4)
        // Outside the cards, so it stands on its own.
        container = root
        add(button("Save") { save() }.apply {
            // Highlighted: settings only take effect after saving.
            backgroundTintList = ColorStateList.valueOf(ACCENT)
            setTextColor(Color.BLACK)
        }, top = 12)

        section("Debugging", key = "open_log", defaultOpen = true)
        add(help("What the watch sent and how it went, newest first. Useful when something does not work."), top = 0)
        log = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(MUTED)
        }
        add(button("Check watch connection") {
            WatchLink.check(this)
            refreshLog()
            // The watch's answer takes a moment; after 10 s without one the check logs that too.
            log.postDelayed({ refreshLog() }, 3000)
            log.postDelayed({ refreshLog() }, 11000)
        }, top = 8)
        add(help("\"Connected\" only means Bluetooth. The watch app is reachable only if \"watch answered\" appears."), top = 8)
        add(log, top = 8)
        logMore = button("") {
            showAllLog = !showAllLog
            refreshLog()
        }.also { add(it, top = 8) }
        updateSummaries()

        container = root
        add(help(DISCLAIMER).apply {
            textSize = 11f
            gravity = android.view.Gravity.CENTER
        }, top = 24)

        // Android 15+ draws apps behind the status and navigation bars; keep the content clear of them.
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }
        setContentView(ScrollView(this).apply { addView(root) })

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        WatchService.start(this)
        openNotificationSwitch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openNotificationSwitch(intent)
    }

    /** "Hide this notification" in the notification leads here. */
    private fun openNotificationSwitch(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_HIDE_NOTIFICATION, false) == true) {
            intent.removeExtra(EXTRA_HIDE_NOTIFICATION)
            startActivity(WatchService.channelSettings(this))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
        updateSetupStatus()
        // Back from the settings pages of the getting-started steps.
        if (tidalFailed) loadFromTidal()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun save() {
        config.genres = checkedGenres()
        config.searchTerms = terms.text.lines()
        config.clientId = clientId.text.toString()
        config.clientSecret = clientSecret.text.toString()
        config.country = country.text.toString()
        config.autoShuffle = autoShuffle.isChecked
        updateSummaries()
        WatchService.start(this) // picks up new API credentials
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    /** Refreshes the genre list (and the cached mix names) from Tidal, keeping the current ticks. */
    private fun loadFromTidal() {
        if (loading) return
        loading = true
        scope.launch {
            val tidal = TidalController(this@MainActivity)
            try {
                val available = tidal.genreNames()
                if (available.isNotEmpty()) {
                    config.cachedGenres = available
                    showGenres(available, checkedGenres())
                }
                genreInfo.text = ""
                genreInfo.visibility = View.GONE
                tidalFailed = false
                gettingStarted.visibility = View.GONE
                tidal.mixNames().let { if (it.isNotEmpty()) config.cachedMixes = it }
            } catch (e: Exception) {
                genreInfo.text = "Could not load Tidal's genres (${e.message ?: e.javaClass.simpleName}). Showing the last known list."
                genreInfo.visibility = View.VISIBLE
                tidalFailed = true
                gettingStarted.visibility = View.VISIBLE
            } finally {
                loading = false
            }
        }
    }

    private fun showGenres(available: List<String>, chosen: List<String>) {
        availableGenres = available
        chosenGenres = chosen
        genreSummary.text = chosen.joinToString(", ").ifEmpty { "No genres chosen" }
        updateSummaries()
    }

    private fun checkedGenres(): List<String> = chosenGenres

    /**
     * The full checklist in a pop-up: the chosen genres first, in their order, then the rest of Tidal's list.
     * Genres that stay ticked keep their place on the watch; newly ticked ones go at the end.
     */
    private fun editGenres() {
        val items = chosenGenres + availableGenres.filter { it !in chosenGenres }
        val checked = BooleanArray(items.size) { items[it] in chosenGenres }
        AlertDialog.Builder(this)
            .setTitle("Genres on the watch")
            .setMultiChoiceItems(items.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("OK") { _, _ ->
                val picked = items.filterIndexed { i, _ -> checked[i] }
                showGenres(availableGenres, chosenGenres.filter { it in picked } + picked.filter { it !in chosenGenres })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** The one-line summaries shown next to a folded section's heading. */
    private fun updateSummaries() {
        if (!::radioSection.isInitialized || !::otherSection.isInitialized) return
        genresSection.summary.text = "${chosenGenres.size} genres"
        searchSection.summary.text = "${terms.text.lines().count { it.isNotBlank() }} search terms"
        val radio = clientId.text.isNotBlank() && clientSecret.text.isNotBlank()
        radioSection.summary.text = if (radio) "✓ Set up" else "Not set up"
        otherSection.summary.text = "Auto-shuffle " + if (autoShuffle.isChecked) "on" else "off"
    }

    private fun openAppInfo(pkg: String) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }

    private fun requestBatteryExemption() {
        // The quick Allow dialog only appears while the app is still restricted; afterwards show its App info.
        if (getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            openAppInfo(packageName)
        } else {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    /** Returns true if all three settings are in place. */
    private fun updateSetupStatus(): Boolean {
        val access = TidalAccessListener.isEnabled(this)
        val power = getSystemService(PowerManager::class.java)
        val battery = power.isIgnoringBatteryOptimizations(packageName)
        val tidalBattery = power.isIgnoringBatteryOptimizations(TidalController.TIDAL)
        // Each line in its own colour, so one missing setting doesn't mark the others.
        val lines = listOf(
            access to (if (access) "Notification access granted" else "Notification access not granted"),
            battery to (if (battery) "Tidal Remote is running unrestricted" else "Tidal Remote may be battery restricted"),
            tidalBattery to (if (tidalBattery) "Tidal is running unrestricted" else "Tidal may be battery restricted"),
        )
        val text = SpannableStringBuilder()
        lines.forEachIndexed { i, (ok, line) ->
            if (i > 0) text.append("\n")
            val start = text.length
            text.append(if (ok) "✓ " else "✗ ").append(line)
            text.setSpan(ForegroundColorSpan(if (ok) ACCENT else WARNING), start, text.length, 0)
        }
        setupStatus.text = text
        val allOk = lines.all { it.first }
        setupSection.summary.text = if (allOk) "✓ All set" else "✗ Needs attention"
        setupSection.summary.setTextColor(if (allOk) ACCENT else WARNING)
        // Something went missing: show the buttons again.
        if (!allOk) setupSection.open = true
        return allOk
    }

    private fun refreshLog() {
        val lines = CommandLog.read(this)
        log.text = (if (showAllLog) lines else lines.take(LOG_LINES)).joinToString("\n").ifEmpty { "No commands yet." }
        logMore.visibility = if (lines.size > LOG_LINES) View.VISIBLE else View.GONE
        logMore.text = if (showAllLog) "Show fewer" else "Show all (${lines.size})"
    }

    // --- layout helpers ---

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun add(view: View, top: Int = 4) {
        container.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        })
    }

    /** A rounded card with a heading. A collapsible one folds away when its heading is tapped. */
    private inner class Section(private val title: String, private val collapsible: Boolean) {
        val card = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val body = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        private val heading = TextView(this@MainActivity).apply {
            textSize = 18f
            setTextColor(ACCENT)
            setTypeface(typeface, Typeface.BOLD)
        }

        /** Shown next to the heading while the section is folded. */
        val summary = TextView(this@MainActivity).apply {
            textSize = 13f
            setTextColor(MUTED)
            gravity = Gravity.END
        }
        var onToggle: ((Boolean) -> Unit)? = null

        var open = true
            set(value) {
                field = value
                body.visibility = if (value) View.VISIBLE else View.GONE
                summary.visibility = if (value) View.GONE else View.VISIBLE
                heading.text = if (collapsible) (if (value) "▾  " else "▸  ") + title else title
            }

        init {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(summary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            if (collapsible) row.setOnClickListener {
                open = !open
                onToggle?.invoke(open)
            }
            card.addView(row)
            card.addView(body)
            open = true
        }
    }

    /**
     * Starts a section; following add() calls go into it. With a [key] it is collapsible and remembers
     * whether it was left open or folded.
     */
    private fun section(title: String, collapsible: Boolean = false, key: String? = null, defaultOpen: Boolean = true): Section {
        val s = Section(title, collapsible || key != null)
        root.addView(s.card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        container = s.body
        if (key != null) {
            val ui = getSharedPreferences("ui", MODE_PRIVATE)
            s.open = ui.getBoolean(key, defaultOpen)
            s.onToggle = { ui.edit().putBoolean(key, it).apply() }
        }
        return s
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun help(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(MUTED)
    }

    /** Text fields: a rounded outline box and an accent-coloured cursor (the default cursor is black and invisible here). */
    private fun styleInput(e: EditText) {
        e.background = GradientDrawable().apply {
            setStroke(dp(1), ACCENT)
            cornerRadius = dp(8).toFloat()
        }
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        e.textSize = 16f
        if (Build.VERSION.SDK_INT >= 29) {
            e.textCursorDrawable = GradientDrawable().apply {
                setColor(ACCENT)
                setSize(dp(2), 0)
            }
        }
    }

    private fun multiline(lines: List<String>) = EditText(this).apply {
        setText(lines.joinToString("\n"))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 3
        styleInput(this)
    }

    private fun field(hint: String, value: String, secret: Boolean) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or
            (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL)
        isSingleLine = true
        // After isSingleLine, which would otherwise replace the password masking.
        if (secret) transformationMethod = PasswordTransformationMethod.getInstance()
        styleInput(this)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
