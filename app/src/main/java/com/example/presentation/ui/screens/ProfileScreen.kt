package com.example.presentation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auth.AccountEntity
import com.example.presentation.mvi.DeskIntent
import com.example.presentation.mvi.DeskUiState
import com.example.presentation.ui.components.LocalWeatherEnvironmentBar
import com.example.presentation.ui.components.PetMilestoneJournalSection
import com.example.presentation.ui.components.ThemeSelectorCard
import com.example.presentation.ui.components.UserProfileCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profile: the signed-in account, the editable Nooki profile, settings, the journal, and log out.
 */
@Composable
fun ProfileScreen(
    account: AccountEntity,
    uiState: DeskUiState,
    onOpenNoticesAndReliability: () -> Unit,
    onSyncGpsWeather: () -> Unit,
    onLogOut: () -> Unit,
    onIntent: (DeskIntent) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 620.dp)
            .testTag("screen_profile"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Profile", subtitle = "Your account, settings and Nooki's journal")
        }

        // Account
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = scheme.primary) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = account.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimary
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface
                        )
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.testTag("profile_email")
                        )
                        Text(
                            text = "Member since " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                .format(Date(account.createdAtMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Text(
                    text = "Your data is kept in its own space on this phone. Other accounts can't see it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }

        item {
            UserProfileCard(
                userProfile = uiState.userProfile,
                level = uiState.level,
                totalXp = uiState.totalXp,
                happinessScore = uiState.moodState.happinessScore,
                todoCount = uiState.todoTasks.size,
                doneCount = uiState.doneTasks.size,
                streakState = uiState.consistencyStreak,
                isSoundEnabled = uiState.isSoundEnabled,
                isHapticsEnabled = uiState.isHapticsEnabled,
                onToggleSound = { onIntent(DeskIntent.ToggleSoundEnabled) },
                onToggleHaptics = { onIntent(DeskIntent.ToggleHapticsEnabled) },
                onPreviewCue = { cue -> onIntent(DeskIntent.PreviewSensoryCue(cue)) },
                onSaveProfile = { name, title, badge, motto, goal ->
                    onIntent(
                        DeskIntent.SaveUserProfile(
                            displayName = name,
                            companionTitle = title,
                            avatarBadge = badge,
                            bioMotto = motto,
                            dailyReminderGoal = goal
                        )
                    )
                }
            )
        }

        // Settings
        item {
            SectionCard(title = "Settings") {
                SettingsRow(
                    title = "Notices & reminder reliability",
                    subtitle = if (uiState.noticeSuggestions.isEmpty()) {
                        "What Nooki notices, alarm permission, diagnostics"
                    } else {
                        "${uiState.noticeSuggestions.size} suggestion(s) waiting"
                    },
                    onClick = onOpenNoticesAndReliability,
                    modifier = Modifier.testTag("open_notices")
                )
            }
        }
        item {
            ThemeSelectorCard(
                selectedTheme = uiState.selectedTheme,
                onSelectTheme = { theme -> onIntent(DeskIntent.SelectAppTheme(theme)) }
            )
        }
        item {
            LocalWeatherEnvironmentBar(
                weatherState = uiState.weatherState,
                onSyncGpsWeather = onSyncGpsWeather,
                onSelectWeatherCondition = { condition -> onIntent(DeskIntent.SelectWeatherOverride(condition)) }
            )
        }
        item {
            PetMilestoneJournalSection(
                entries = uiState.journalEntries,
                selectedFilter = uiState.selectedJournalFilter,
                onSelectFilter = { filter -> onIntent(DeskIntent.SelectJournalFilter(filter)) },
                onAddCustomMilestone = { title, narrative, type ->
                    onIntent(DeskIntent.AddCustomJournalNote(title = title, narrative = narrative, milestoneType = type))
                },
                onDeleteEntry = { id -> onIntent(DeskIntent.DeleteJournalEntry(id)) }
            )
        }

        item {
            OutlinedButton(
                onClick = { confirmLogout = true },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log out / switch account")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = {
                Text("Your data stays saved for next time. Your reminders won't ring until you log back in.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLogout = false
                        onLogOut()
                    },
                    modifier = Modifier.testTag("confirm_logout")
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
