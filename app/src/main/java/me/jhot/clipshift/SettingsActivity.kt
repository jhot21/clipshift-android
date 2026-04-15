package me.jhot.clipshift

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.jhot.clipshift.ui.theme.ClipShiftTheme
import java.security.SecureRandom

private val TOPIC_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
private const val TOPIC_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipShiftTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val cfg = remember { Config.load(context) }

    var deviceName by rememberSaveable { mutableStateOf(cfg.deviceName) }
    var topic by rememberSaveable { mutableStateOf(cfg.topic) }
    var baseUrl by rememberSaveable { mutableStateOf(cfg.baseUrl) }
    // Intentionally remember (not rememberSaveable) — passphrase must not be written
    // into the saved instance state bundle. On rotation it re-reads from EncryptedPrefs.
    var passphrase by remember {
        mutableStateOf(if (cfg.encryptionEnabled) Config.loadPassphrase(context) ?: "" else "")
    }
    var encryptionEnabled by rememberSaveable { mutableStateOf(cfg.encryptionEnabled) }
    var maxAttachmentSizeMb by rememberSaveable { mutableStateOf(cfg.maxAttachmentSizeMb.toString()) }
    var showTopicError by rememberSaveable { mutableStateOf(false) }
    var showPassphraseError by rememberSaveable { mutableStateOf(false) }
    var showBanner by rememberSaveable { mutableStateOf(cfg.topic.isBlank()) }
    var showPermissionWarning by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showPermissionWarning = !granted
        startServiceIfTopicSet(context)
    }

    LaunchedEffect(Unit) {
        startServiceIfTopicSet(context)
    }

    Scaffold(
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (showBanner) {
                Text(
                    text = stringResource(R.string.onboarding_banner),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (showPermissionWarning) {
                Text(
                    text = stringResource(R.string.permission_notification_denied),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text(stringResource(R.string.label_device_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.hint_base_url)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.label_topic)) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val rng = SecureRandom()
                        val suffix = (1..16).map { TOPIC_CHARS[rng.nextInt(TOPIC_CHARS.length)] }.joinToString("")
                        topic = "clipshift-$suffix"
                    }
                ) {
                    Text(stringResource(R.string.btn_generate))
                }
            }

            if (showTopicError) {
                Text(
                    text = stringResource(R.string.error_topic_invalid),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_encryption),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = encryptionEnabled,
                    onCheckedChange = { encryptionEnabled = it },
                )
            }

            AnimatedVisibility(visible = encryptionEnabled) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.label_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            OutlinedTextField(
                value = maxAttachmentSizeMb,
                onValueChange = { maxAttachmentSizeMb = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.label_max_attachment_size)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            if (showPassphraseError) {
                Text(
                    text = stringResource(R.string.error_passphrase_required),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Button(
                onClick = {
                    val trimmedTopic = topic.trim()
                    val trimmedDeviceName = deviceName.trim()
                    val trimmedBaseUrl = baseUrl.trim()

                    var valid = true
                    if (!TOPIC_REGEX.matches(trimmedTopic)) {
                        showTopicError = true
                        valid = false
                    } else {
                        showTopicError = false
                    }
                    if (encryptionEnabled && passphrase.isBlank()) {
                        showPassphraseError = true
                        valid = false
                    } else {
                        showPassphraseError = false
                    }
                    if (!valid) return@Button

                    Config.save(
                        context,
                        topic = trimmedTopic,
                        deviceName = trimmedDeviceName,
                        encryptionEnabled = encryptionEnabled,
                        baseUrl = trimmedBaseUrl,
                        maxAttachmentSizeMb = maxAttachmentSizeMb.trim().toIntOrNull()?.coerceAtLeast(1) ?: 15,
                    )
                    if (encryptionEnabled) Config.savePassphrase(context, passphrase)
                    else Config.savePassphrase(context, null)

                    showBanner = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startServiceIfTopicSet(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    }
}

private fun startServiceIfTopicSet(context: Context) {
    if (Config.load(context).topic.isNotBlank()) {
        context.startForegroundService(Intent(context, ClipShiftService::class.java))
    }
}
