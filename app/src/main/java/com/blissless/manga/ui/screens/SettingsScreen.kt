package com.blissless.manga.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blissless.manga.MainActivity
import com.blissless.manga.viewmodel.MainViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val anilistUsername by viewModel.anilistUsername.collectAsState()
    val isSyncing by viewModel.isAniListSyncing.collectAsState()
    val syncThreshold by viewModel.anilistSyncThreshold.collectAsState()
    val showMergeDialog by viewModel.showMergeDialog.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkAnilistSession()
    }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    val githubUrl = "https://github.com/Suntrax/kitsune"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Account Section
        SettingsSectionHeader("Account")
        SettingsCard {
            if (anilistUsername != null) {
                SettingsNavItem(
                    icon = Icons.Default.AccountCircle,
                    title = "AniList Account",
                    subtitle = "Logged in as $anilistUsername",
                    trailing = {
                        Text("Logout", color = Color(0xFFef4444), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    },
                    onClick = { showLogoutDialog = true }
                )
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFFa855f7),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Syncing...", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.syncAnilistManga() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFa855f7))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Now", color = Color(0xFFa855f7))
                        }
                    }
                }
            } else {
                SettingsNavItem(
                    icon = Icons.Default.AccountCircle,
                    title = "AniList Account",
                    subtitle = "Log in to sync your list",
                    trailing = {
                        Text("Login", color = Color(0xFFa855f7), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.getAnilistAuthUrl()))
                        context.startActivity(intent)
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionHeader("Sync")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Auto-sync Threshold",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "$syncThreshold%",
                        color = Color(0xFFa855f7),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Sync progress to AniList after reading this % of a chapter",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = syncThreshold.toFloat(),
                    onValueChange = { viewModel.updateAnilistSyncThreshold(it.roundToInt()) },
                    valueRange = 75f..100f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFa855f7),
                        activeTrackColor = Color(0xFFa855f7),
                        inactiveTrackColor = Color(0xFF2a2a3a),
                        inactiveTickColor = Color(0xFFa855f7)
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // About Section
        SettingsSectionHeader("About")
        SettingsCard {
            SettingsNavItem(
                icon = Icons.Default.Info,
                title = "Kitsune Manga Reader",
                subtitle = "Version 1.4",
                trailing = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
                },
                onClick = { showGitHubDialog = true }
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showGitHubDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = {
                Text("Open GitHub", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Open the GitHub page for Kitsune Manga Reader?",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGitHubDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFa855f7)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitHubDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    if (showMergeDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF1a1a2e),
            title = {
                Text("Local manga found", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "You have locally saved manga. How would you like to proceed?",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.overwriteAnilistWithLocal() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFa855f7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Overwrite AniList with local")
                    }
                    Button(
                        onClick = { viewModel.discardLocalAndSync() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Discard local, use AniList")
                    }
                    TextButton(
                        onClick = { viewModel.mergeLocalAndAnilist() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Merge – only add new entries", color = Color(0xFFa855f7))
                    }
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = {
                Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to logout from AniList? Your locally synced manga will not be removed.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logoutAniList()
                        (context as? MainActivity)?.resetAuthFlags()
                        showLogoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFFa855f7),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1a1a2e))
    ) {
        content()
    }
}

@Composable
fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFa855f7), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = Color.White.copy(alpha = 0.06f)
    )
}
