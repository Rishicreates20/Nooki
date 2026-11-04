package com.example.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.PetJournalEntryEntity
import com.example.data.local.entity.PetMilestoneType
import com.example.domain.model.NookiAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Theme Selector Card allowing the user to switch Nooki's color palette
 * (e.g., 'Midnight Cozy', 'Forest Meadow', 'Warm Hearth', 'Monsoon Lotus') and
 * automatically attune Nooki's 3D orbital & touch-burst particle colors to match.
 */
@Composable
fun ThemeSelectorCard(
    selectedTheme: NookiAppTheme,
    onSelectTheme: (NookiAppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .testTag("theme_selector_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Theme Selector",
                        tint = scheme.primary
                    )
                    Column {
                        Text(
                            text = "Sanctuary Theme & 3D Particles",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface
                        )
                        Text(
                            text = "Active: ${selectedTheme.badgeEmoji} ${selectedTheme.displayName} • Auto-updates 3D particle colors",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            // Live 3D Particle Palette Preview Bar for the currently selected theme
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .testTag("active_theme_particle_preview")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "3D Particle Colors",
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Synced 3D Particle Palette:",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onBackground
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedTheme.particleColorsHex.forEachIndexed { idx, hex ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                                    .testTag("particle_swatch_${selectedTheme.id}_$idx")
                            )
                        }
                    }
                }
            }

            // Theme Option Cards ('Midnight Cozy', 'Forest Meadow', 'Warm Hearth', 'Monsoon Lotus')
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NookiAppTheme.entries.forEach { themeOption ->
                    val isSelected = themeOption == selectedTheme
                    val optionBg = Color(themeOption.backgroundDeepHex)
                    val optionSurface = Color(themeOption.surfaceCardHex)
                    val optionPrimary = Color(themeOption.primaryAccentHex)
                    val optionSecondary = Color(themeOption.secondaryAccentHex)
                    val optionText = Color(themeOption.textPrimaryHex)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = optionSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) optionPrimary else optionPrimary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectTheme(themeOption) }
                            .testTag("theme_option_${themeOption.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(optionBg)
                                        .border(1.5.dp, optionPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = themeOption.badgeEmoji,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = themeOption.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = optionText,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected theme",
                                                tint = optionPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = themeOption.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = optionText.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Theme & Particle Swatch Dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(optionPrimary)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(optionSecondary)
                                )
                                themeOption.particleColorsHex.take(2).forEach { pHex ->
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(pHex))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pet Milestone Journal Section displayed in the Profile screen.
 * Records significant pet milestones (e.g. 'first high-streak achievement', 'pet personality shifts',
 * '3D bonding moments', 'atmosphere shifts') and renders them in a scrollable history feed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetMilestoneJournalSection(
    entries: List<PetJournalEntryEntity>,
    selectedFilter: PetMilestoneType?,
    onSelectFilter: (PetMilestoneType?) -> Unit,
    onAddCustomMilestone: (String, String, PetMilestoneType) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var isComposerExpanded by rememberSaveable { mutableStateOf(false) }
    var customTitle by rememberSaveable { mutableStateOf("") }
    var customNarrative by rememberSaveable { mutableStateOf("") }
    var selectedNewType by rememberSaveable { mutableStateOf(PetMilestoneType.BONDING_MOMENT) }

    val filteredEntries = remember(entries, selectedFilter) {
        if (selectedFilter == null) {
            entries
        } else {
            entries.filter { it.milestoneType == selectedFilter }
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, scheme.secondary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .testTag("pet_milestone_journal_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "Pet Milestone Journal",
                        tint = scheme.primary
                    )
                    Column {
                        Text(
                            text = "Nooki's Milestone Journal",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface
                        )
                        Text(
                            text = "${entries.size} recorded milestones • High streaks, personality shifts & memories",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface.copy(alpha = 0.78f)
                        )
                    }
                }

                IconButton(
                    onClick = { isComposerExpanded = !isComposerExpanded },
                    modifier = Modifier.testTag("toggle_journal_composer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Record a custom milestone",
                        tint = scheme.primary
                    )
                }
            }

            // Milestone Category Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onSelectFilter(null) },
                    label = { Text("All (${entries.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primary,
                        selectedLabelColor = scheme.onPrimary
                    ),
                    modifier = Modifier.testTag("journal_filter_ALL")
                )
                PetMilestoneType.entries.forEach { milestoneType ->
                    val count = entries.count { it.milestoneType == milestoneType }
                    FilterChip(
                        selected = selectedFilter == milestoneType,
                        onClick = {
                            onSelectFilter(if (selectedFilter == milestoneType) null else milestoneType)
                        },
                        label = {
                            Text("${milestoneType.defaultBadgeEmoji} ${milestoneType.categoryLabel} ($count)")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = scheme.primary,
                            selectedLabelColor = scheme.onPrimary
                        ),
                        modifier = Modifier.testTag("journal_filter_${milestoneType.name}")
                    )
                }
            }

            // Expandable Custom Milestone Composer
            AnimatedVisibility(visible = isComposerExpanded) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Log a Pet Milestone Memory",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PetMilestoneType.entries.forEach { mType ->
                                FilterChip(
                                    selected = selectedNewType == mType,
                                    onClick = { selectedNewType = mType },
                                    label = { Text("${mType.defaultBadgeEmoji} ${mType.categoryLabel}") }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            placeholder = { Text("Milestone title (e.g. 'First 7-day streak together!')") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = scheme.primary,
                                focusedTextColor = scheme.onBackground,
                                unfocusedTextColor = scheme.onBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("journal_title_input")
                        )

                        OutlinedTextField(
                            value = customNarrative,
                            onValueChange = { customNarrative = it },
                            placeholder = { Text("Optional story or note about Nooki's mood...") },
                            singleLine = false,
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = scheme.primary,
                                focusedTextColor = scheme.onBackground,
                                unfocusedTextColor = scheme.onBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("journal_narrative_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isComposerExpanded = false }) {
                                Text("Cancel", color = scheme.onBackground)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (customTitle.isNotBlank()) {
                                        onAddCustomMilestone(customTitle, customNarrative, selectedNewType)
                                        customTitle = ""
                                        customNarrative = ""
                                        isComposerExpanded = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary
                                ),
                                modifier = Modifier.testTag("save_journal_milestone_button")
                            ) {
                                Text("Save Milestone", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Scrollable Milestone History Feed inside the Profile card
            val feedScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 380.dp)
                    .verticalScroll(feedScrollState)
                    .testTag("journal_scrollable_history_feed")
            ) {
                if (filteredEntries.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.background.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No milestones in this category yet. Complete tasks, build streaks, or interact with Nooki to record new milestones!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        filteredEntries.forEach { entry ->
                            JournalMilestoneItemRow(
                                entry = entry,
                                onDelete = { onDeleteEntry(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalMilestoneItemRow(
    entry: PetJournalEntryEntity,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val badgeAccent = when (entry.milestoneType) {
        PetMilestoneType.FIRST_HIGH_STREAK -> scheme.primary
        PetMilestoneType.PERSONALITY_SHIFT -> scheme.secondary
        PetMilestoneType.BONDING_MOMENT -> Color(0xFFFF8FA3)
        PetMilestoneType.THEME_ATMOSPHERE -> Color(0xFFA7F3D0)
        PetMilestoneType.TASK_CHAMPION -> scheme.secondary
    }

    val formattedTime = remember(entry.timestampMillis) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(entry.timestampMillis))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("journal_entry_row_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Thread Timeline Spine with Knot
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                drawLine(
                    color = badgeAccent.copy(alpha = 0.55f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = badgeAccent,
                    radius = 5.5.dp.toPx(),
                    center = Offset(cx, size.height / 2f)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.background,
            modifier = Modifier
                .weight(1f)
                .border(1.dp, badgeAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.surface)
                            .border(1.dp, badgeAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = entry.badgeEmoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = entry.milestoneType.categoryLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• $formattedTime",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = entry.narrative,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onBackground.copy(alpha = 0.84f)
                        )
                        Text(
                            text = "Mood Snapshot: ${entry.happinessSnapshot}% Happy • 🔥 ${entry.streakSnapshot}d Streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeAccent.copy(alpha = 0.9f)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_journal_entry_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete milestone entry",
                        tint = scheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
