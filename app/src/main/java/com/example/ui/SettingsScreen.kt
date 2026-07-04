package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CryptoManager
import com.example.data.MediaUtils
import com.example.ui.theme.CipherRed
import com.example.viewmodel.CipherViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: CipherViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showEditProfile by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Profile card
        val me = currentUser
        if (me != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Avatar(name = me.displayName, avatarUrl = me.avatarUrl, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(me.displayName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("@${me.username}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (me.bio.isNotBlank()) {
                        Text(me.bio, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { showEditProfile = true }) { Text("Edit") }
            }

            // Security fingerprint
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Key fingerprint",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = CryptoManager.getFingerprint(me.publicKey).take(47),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // Appearance
        SettingRow(title = "Dark theme") {
            Switch(
                checked = settings.themeMode == "DARK",
                onCheckedChange = { viewModel.updateThemeMode(if (it) "DARK" else "LIGHT") }
            )
        }

        SettingRow(title = "Notifications") {
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
            )
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Message text size: ${settings.fontSize}sp", fontSize = 15.sp)
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { viewModel.updateFontSize(it.toInt()) },
                valueRange = 12f..20f,
                steps = 3
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CipherRed)
            Spacer(Modifier.width(8.dp))
            Text("Log out", color = CipherRed)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "CipherChat · End-to-end encrypted\nYour private key never leaves this device.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }

    if (showEditProfile && currentUser != null) {
        var displayName by remember { mutableStateOf(currentUser?.displayName ?: "") }
        var bio by remember { mutableStateOf(currentUser?.bio ?: "") }
        var avatarUrl by remember { mutableStateOf(currentUser?.avatarUrl ?: "") }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    // Avatars are small: 512px max, ~80KB budget
                    val b64 = MediaUtils.uriToCompressedBase64(context, uri, maxDimension = 512, maxBytes = 80_000)
                    if (b64 != null) avatarUrl = "b64:$b64"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = displayName, avatarUrl = avatarUrl, size = 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            TextButton(onClick = {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }) { Text("Choose photo") }
                            if (avatarUrl.isNotBlank()) {
                                TextButton(onClick = { avatarUrl = "" }) { Text("Remove photo") }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (displayName.isNotBlank()) {
                        viewModel.updateProfile(displayName.trim(), bio.trim(), avatarUrl)
                    }
                    showEditProfile = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) { Text("Cancel") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("Local data on this device will be cleared. Your messages remain encrypted in the cloud and will re-sync at next login — but messages sent before this login may be unreadable on a fresh device since your private key lives only here.") },
            confirmButton = {
                Button(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout()
                }) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingRow(title: String, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}
