package me.jhot.clipshift

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.security.SecureRandom

private val TOPIC_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
private const val TOPIC_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

class SettingsActivity : AppCompatActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            findViewById<View>(R.id.notificationPermissionWarning).visibility =
                if (granted) View.GONE else View.VISIBLE
            startServiceIfTopicSet()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scrollView)) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, top, view.paddingRight, view.paddingBottom)
            insets
        }

        val cfg = Config.load(this)

        // Pre-populate fields
        findViewById<TextInputEditText>(R.id.deviceNameInput).setText(cfg.deviceName)
        findViewById<TextInputEditText>(R.id.topicInput).setText(cfg.topic)
        val encryptionToggle = findViewById<SwitchMaterial>(R.id.encryptionToggle)
        encryptionToggle.isChecked = cfg.encryptionEnabled
        if (cfg.encryptionEnabled) {
            Config.loadPassphrase(this)?.let {
                findViewById<TextInputEditText>(R.id.passphraseInput).setText(it)
            }
        }

        // Show onboarding banner if no topic
        if (cfg.topic.isBlank()) {
            findViewById<View>(R.id.bannerText).visibility = View.VISIBLE
        }

        // Passphrase visibility tied to toggle
        val passphraseLayout = findViewById<View>(R.id.passphraseLayout)
        passphraseLayout.visibility = if (cfg.encryptionEnabled) View.VISIBLE else View.GONE
        encryptionToggle.setOnCheckedChangeListener { _, checked ->
            passphraseLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // Generate button
        findViewById<MaterialButton>(R.id.generateBtn).setOnClickListener {
            val rng = SecureRandom()
            val suffix = (1..16).map { TOPIC_CHARS[rng.nextInt(TOPIC_CHARS.length)] }.joinToString("")
            findViewById<TextInputEditText>(R.id.topicInput).setText("clipshift-$suffix")
        }

        // Subscribe button
        findViewById<MaterialButton>(R.id.subscribeBtn).setOnClickListener {
            val topic = findViewById<TextInputEditText>(R.id.topicInput).text?.toString().orEmpty()
            if (topic.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("io.heckel.ntfy://subscribe/$topic")))
            }
        }

        // Save button
        findViewById<MaterialButton>(R.id.saveBtn).setOnClickListener { onSave() }

        // Restart service if topic is already set
        startServiceIfTopicSet()
    }

    private fun onSave() {
        val topic = findViewById<TextInputEditText>(R.id.topicInput).text?.toString().orEmpty().trim()
        val deviceName = findViewById<TextInputEditText>(R.id.deviceNameInput).text?.toString().orEmpty().trim()
        val encryptionEnabled = findViewById<SwitchMaterial>(R.id.encryptionToggle).isChecked
        val passphrase = findViewById<TextInputEditText>(R.id.passphraseInput).text?.toString().orEmpty()

        var valid = true

        if (!TOPIC_REGEX.matches(topic)) {
            findViewById<View>(R.id.topicError).visibility = View.VISIBLE
            valid = false
        } else {
            findViewById<View>(R.id.topicError).visibility = View.GONE
        }

        if (encryptionEnabled && passphrase.isBlank()) {
            findViewById<View>(R.id.passphraseError).visibility = View.VISIBLE
            valid = false
        } else {
            findViewById<View>(R.id.passphraseError).visibility = View.GONE
        }

        if (!valid) return

        val baseUrl = Config.load(this).baseUrl
        Config.save(this, topic = topic, deviceName = deviceName,
            encryptionEnabled = encryptionEnabled, paused = false, baseUrl = baseUrl)
        if (encryptionEnabled) Config.savePassphrase(this, passphrase)
        else Config.savePassphrase(this, null)

        // Hide onboarding banner
        findViewById<View>(R.id.bannerText).visibility = View.GONE

        // Request notification permission on API 33+, then start service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startServiceIfTopicSet()
        }
    }

    private fun startServiceIfTopicSet() {
        val cfg = Config.load(this)
        if (cfg.topic.isNotBlank()) {
            startForegroundService(Intent(this, ClipShiftService::class.java))
        }
    }
}
