package io.github.jumpypangolin.tidalremote

import android.Manifest
import android.app.Activity
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
import android.text.method.PasswordTransformationMethod
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
        private val ACCENT = Color.rgb(0, 170, 170)
        private val MUTED = Color.rgb(160, 160, 160)
        private val WARNING = Color.rgb(230, 120, 80)
        private val CARD = Color.rgb(38, 38, 42)
        private const val DISCLAIMER = "Tidal Remote is an independent, non-commercial hobby project. " +
            "It is not affiliated with, endorsed or sponsored by TIDAL or Garmin. " +
            "TIDAL and Garmin are trademarks of their respective owners."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var config: Config
    private lateinit var root: LinearLayout

    /** Where add() puts views: the page, or the card of the current section. */
    private lateinit var container: LinearLayout
    private lateinit var genres: LinearLayout
    private lateinit var terms: EditText
    private lateinit var clientId: EditText
    private lateinit var clientSecret: EditText
    private lateinit var country: EditText
    private lateinit var autoShuffle: CheckBox
    private lateinit var setupStatus: TextView
    private lateinit var genreInfo: TextView
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = Config(this)
        val pad = dp(16)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        container = root

        add(TextView(this).apply {
            text = "Tidal Remote"
            textSize = 24f
        })
        add(help("Start Tidal playlists and control playback from your Garmin watch. Configure settings below and click Save. " +
            "Updates will be reflected on the watch after closing and re-opening the app."), top = 8)

        section("1. Phone setup")
        add(help("Notification access is mandatory for Tidal to accept commands. Battery exception is strongly recommended."))
        add(button("Allow access to Tidal (notification access)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, top = 12)
        add(button("Allow running in background (battery)") { requestBatteryExemption() }, top = 8)
        setupStatus = help("").also { add(it, top = 8) }

        section("2. Watch lists")
        add(help("\"My Mix\" section is populated automatically from Tidal."))
        add(label("Genres"), top = 12)
        add(help("Select the genres to be available on the watch. The app will then play a random playlist from the chosen genre."))
        genres = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { add(it) }
        genreInfo = help("").also { add(it) }
        showGenres(config.cachedGenres, config.genres)
        loadFromTidal()
        add(label("Search terms"), top = 16)
        add(help("One per line, any words. The watch plays a random playlist from Tidal's search results."))
        terms = multiline(config.searchTerms).also { add(it) }

        section("3. Track radio (optional)")
        add(help("Only needed for Track radio. Sign in at developer.tidal.com with your Tidal account, " +
            "create an app, and copy its Client ID and Client secret here."))
        add(label("Client ID"))
        clientId = field("Client ID", config.clientId, false).also { add(it) }
        add(label("Client secret"), top = 16)
        clientSecret = field("Client secret", config.clientSecret, true).also { add(it) }
        add(label("Country of your Tidal account"), top = 16)
        add(help("The two-letter code of the country where you signed up for Tidal, for example CH (Switzerland), " +
            "DE (Germany), GB (United Kingdom), BR (Brazil), US (USA). Pre-filled from your SIM card. " +
            "Track radio looks up its songs for this country."))
        country = field("CH", config.country, false).also { add(it) }

        section("Options")
        autoShuffle = CheckBox(this).apply {
            text = "Auto-shuffle new playlists (experimental)"
            isChecked = config.autoShuffle
            // The default tint is black on this dark screen, which hides the tick.
            buttonTintList = ColorStateList.valueOf(ACCENT)
        }
        add(autoShuffle)
        // Outside the cards, so it stands on its own.
        container = root
        add(button("Save") { save() }.apply {
            // Highlighted: settings only take effect after saving.
            backgroundTintList = ColorStateList.valueOf(ACCENT)
            setTextColor(Color.BLACK)
        }, top = 16)

        section("Recent commands")
        add(help("What the watch sent and how it went, newest first. Useful when something does not work."))
        log = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(MUTED)
        }
        add(log, top = 8)

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
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
        updateSetupStatus()
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
        WatchService.start(this) // picks up new API credentials
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    /** Refreshes the genre list (and the cached mix names) from Tidal, keeping the current ticks. */
    private fun loadFromTidal() {
        scope.launch {
            val tidal = TidalController(this@MainActivity)
            try {
                val available = tidal.genreNames()
                if (available.isNotEmpty()) {
                    config.cachedGenres = available
                    showGenres(available, checkedGenres())
                }
                genreInfo.text = ""
                tidal.mixNames().let { if (it.isNotEmpty()) config.cachedMixes = it }
            } catch (e: Exception) {
                genreInfo.text = "Could not load Tidal's genres (${e.message ?: e.javaClass.simpleName}). Showing the last known list."
            }
        }
    }

    /** One checkbox per genre: the chosen ones first, in their order, then the rest of Tidal's list. */
    private fun showGenres(available: List<String>, chosen: List<String>) {
        genres.removeAllViews()
        for (name in chosen + available.filter { it !in chosen }) {
            genres.addView(CheckBox(this).apply {
                text = name
                isChecked = name in chosen
                buttonTintList = ColorStateList.valueOf(ACCENT)
            })
        }
    }

    private fun checkedGenres(): List<String> =
        (0 until genres.childCount).map { genres.getChildAt(it) as CheckBox }.filter { it.isChecked }.map { it.text.toString() }

    private fun requestBatteryExemption() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun updateSetupStatus() {
        val access = TidalAccessListener.isEnabled(this)
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        setupStatus.text = listOf(
            if (access) "✓ Notification access is on" else "✗ Notification access is off: Tidal will refuse commands",
            if (battery) "✓ Allowed to run in the background" else "✗ Battery optimisation is on: the app may stop in the background",
        ).joinToString("\n")
        setupStatus.setTextColor(if (access && battery) ACCENT else WARNING)
    }

    private fun refreshLog() {
        log.text = CommandLog.read(this).joinToString("\n").ifEmpty { "No commands yet." }
    }

    // --- layout helpers ---

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun add(view: View, top: Int = 4) {
        container.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        })
    }

    /** Starts a section: a rounded card with a heading; following add() calls go into it. */
    private fun section(title: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        container = card
        add(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(ACCENT)
            setTypeface(typeface, Typeface.BOLD)
        }, top = 0)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
    }.also { it.setPadding(0, dp(8), 0, 0) }

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
