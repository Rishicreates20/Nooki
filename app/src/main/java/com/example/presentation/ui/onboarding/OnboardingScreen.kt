package com.example.presentation.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.pet.PetMood
import com.example.presentation.mvi.Readiness
import com.example.presentation.ui.components.NookiFace
import com.example.presentation.ui.nooki.ReadinessActions
import com.example.settings.Persona
import com.example.ui.theme.NookiPalette
import com.example.ui.theme.quietButtonColors

/** Five short steps. Nothing is asked for before the moment it clearly helps. */
@Composable
fun OnboardingScreen(
    readiness: Readiness,
    actions: ReadinessActions,
    onFinish: (name: String, persona: Persona, starters: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    initialStep: Int = 0
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var name by rememberSaveable { mutableStateOf("") }
    var persona by rememberSaveable { mutableStateOf(Persona.EVERYONE) }
    val chosen = remember { mutableStateListOf<String>() }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(targetState = step, label = "onboarding_step") { current ->
            when (current) {
                0 -> Step(
                    mood = PetMood.HAPPY,
                    title = "Nooki pays attention, so you don't have to hold it all.",
                    body = "The calls you missed, the things you forgot, and what you have to do. All in one place.",
                    primary = "Get started",
                    onPrimary = { step = 1 }
                )
                1 -> Step(
                    mood = PetMood.HAPPY,
                    title = "Which sounds most like you?",
                    primary = "Continue",
                    onPrimary = {
                        chosen.clear()
                        step = 2
                    }
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your name (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_name")
                    )
                    Spacer(Modifier.height(10.dp))
                    Persona.entries.forEach { option ->
                        PersonaRow(option, selected = option == persona) { persona = option }
                    }
                }
                2 -> Step(
                    mood = PetMood.ALERT,
                    title = "Should Nooki start with any of these?",
                    body = "Pick what's true for you. You can change them later.",
                    primary = if (chosen.isEmpty()) "Skip for now" else "Add ${chosen.size}",
                    onPrimary = { step = 3 }
                ) {
                    persona.starters.forEach { line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (line in chosen) chosen.remove(line) else chosen.add(line) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = line in chosen, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                3 -> Step(
                    mood = PetMood.ALERT,
                    title = "Let Nooki reach you.",
                    body = if (readiness.canPostNotifications) {
                        "All set. Nooki can tap you on the shoulder when something is due."
                    } else {
                        "Reminders only work if Nooki can send a notification."
                    },
                    primary = if (readiness.canPostNotifications) "Continue" else "Allow notifications",
                    onPrimary = {
                        if (readiness.canPostNotifications) step = 4 else actions.requestNotifications()
                    },
                    secondary = if (readiness.canPostNotifications) null else "Not now",
                    onSecondary = { step = 4 }
                )
                else -> Step(
                    mood = PetMood.HAPPY,
                    title = "Should Nooki notice things for you?",
                    primary = if (readiness.noticingEnabled) "Finish" else "Turn on noticing",
                    onPrimary = {
                        if (readiness.noticingEnabled) onFinish(name, persona, chosen.toList()) else actions.openNoticeAccess()
                    },
                    secondary = if (readiness.noticingEnabled) null else "Not now",
                    onSecondary = { onFinish(name, persona, chosen.toList()) }
                ) {
                    Text(
                        "To catch missed calls, unopened messages and bills, Nooki needs to read the notifications " +
                            "that arrive on this phone.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(10.dp))
                    Bullet("It reads them on this phone only. Nooki has no internet permission.")
                    Bullet("It saves who and when, plus the amount and date on a bill. It never saves what a message said.")
                    Bullet("OTPs, passwords and bank balances are dropped straight away.")
                    Bullet("Nothing becomes a reminder until you tap. Turn it off any time.")
                    if (!readiness.noticingEnabled) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "If Android says this is restricted, open App info, tap the three dots at the top, choose " +
                                "Allow restricted settings, then come back here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NookiPalette.colors.muted
                        )
                        TextButton(colors = quietButtonColors(), onClick = actions.openAppInfo) { Text("Open App info") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(
    mood: PetMood,
    title: String,
    primary: String,
    onPrimary: () -> Unit,
    body: String? = null,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            NookiFace(mood = mood, modifier = Modifier.offset(x = (-34).dp).size(132.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("onboarding_title"))
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = NookiPalette.colors.muted)
            }
            Spacer(Modifier.height(16.dp))
            content?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("onboarding_primary")
        ) { Text(primary) }
        if (secondary != null) {
            TextButton(colors = quietButtonColors(), onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 2.dp)) {
            com.example.presentation.ui.today.KnotMark(
                MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
                size = 14.dp
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PersonaRow(option: Persona, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else NookiPalette.colors.raised,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else NookiPalette.colors.line),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(option.label, style = MaterialTheme.typography.titleMedium)
            Text(option.blurb, style = MaterialTheme.typography.bodySmall)
        }
    }
}
