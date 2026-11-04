package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.pet.PetAction
import com.example.pet.PetActionParser
import com.example.pet.PetTopic
import com.example.pet.PetVoice

/**
 * Nooki's speech bubble (PRD FR-2.3 / FR-2.4). The line streams in token by token with a blinking
 * caret, and any `[ACTION:...]` tags become tappable chips once they arrive.
 *
 * @param topicKey restarts the stream when it changes. Keep it stable for the "same" situation so
 *   the line doesn't retype every second as countdowns tick.
 */
@Composable
fun NookiSpeechBubble(
    topic: PetTopic,
    topicKey: String,
    voice: PetVoice,
    onAction: (PetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var raw by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }

    LaunchedEffect(topicKey) {
        raw = ""
        streaming = true
        try {
            voice.stream(topic).collect { token -> raw += token }
        } finally {
            streaming = false
        }
    }

    val parsed = remember(raw) { PetActionParser.parse(raw) }
    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "caret_alpha"
    )
    val bubbleColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        // Square top-left corner points the bubble up at Nooki.
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
        color = bubbleColor,
        modifier = modifier
            .fillMaxWidth()
            .testTag("nooki_speech_bubble")
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(parsed.cleanText)
                    if (streaming) {
                        withStyle(SpanStyle(color = textColor.copy(alpha = caretAlpha))) { append(" ▍") }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            AnimatedVisibility(visible = parsed.actions.isNotEmpty(), enter = fadeIn()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    parsed.actions.forEach { action ->
                        AssistChip(
                            onClick = { onAction(action) },
                            label = { Text(action.label) },
                            leadingIcon = {
                                Icon(
                                    if (action.id == PetAction.MARK_DONE) Icons.Default.CheckCircle else Icons.Default.Snooze,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = null,
                            modifier = Modifier.testTag("pet_action_${action.id}")
                        )
                    }
                }
            }
        }
    }
}
