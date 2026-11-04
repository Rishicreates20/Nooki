package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.example.attention.AttentionEngine
import com.example.demo.SampleDay
import com.example.pet.PetMood
import com.example.presentation.mvi.CaptureDraft
import com.example.presentation.mvi.NookiTab
import com.example.presentation.mvi.NookiUiState
import com.example.presentation.mvi.Readiness
import com.example.presentation.ui.NookiShell
import com.example.presentation.ui.capture.CaptureContent
import com.example.presentation.ui.components.NookiFace
import com.example.presentation.ui.nooki.ReadinessActions
import com.example.presentation.ui.onboarding.OnboardingScreen
import com.example.settings.NookiPrefs
import com.example.settings.Persona
import com.example.ui.theme.NookiPalette
import com.example.ui.theme.NookiTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the real screens to PNG so they can be looked at without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {

    private val now = System.currentTimeMillis()

    private fun state(tab: NookiTab, empty: Boolean = false): NookiUiState {
        val reminders = if (empty) emptyList() else SampleDay.reminders(now)
        val noticed = if (empty) emptyList() else SampleDay.noticed(now)
        val looseEnds = if (empty) emptyList() else SampleDay.looseEnds(now)
        return NookiUiState(
            isLoaded = true,
            prefs = NookiPrefs(onboarded = true, name = "Rishi", persona = Persona.WORKING),
            tab = tab,
            digest = AttentionEngine.build(reminders, noticed, looseEnds, emptyList(), now),
            mood = if (empty) PetMood.SLEEPING else PetMood.ALERT,
            memories = if (empty) emptyList() else SampleDay.memories(now),
            looseEnds = looseEnds,
            level = 3,
            totalXp = 130,
            xpInLevel = 30,
            completedCount = 18,
            caughtThisWeek = 7,
            readiness = Readiness(noticingEnabled = !empty),
            nowMillis = now
        )
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/nooki-shots/$name.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                NookiTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    @Composable
    private fun Shell(state: NookiUiState) {
        NookiShell(
            state = state,
            onIntent = {},
            snackbars = remember { SnackbarHostState() },
            actions = ReadinessActions(),
            onVoice = {},
            showDeveloper = false,
            onOpenDeveloper = {},
            petStage = { modifier ->
                Box(modifier.background(NookiPalette.colors.raised), contentAlignment = Alignment.Center) {
                    NookiFace(state.mood, Modifier.size(210.dp))
                }
            }
        )
    }

    @Test fun today_light() = shot("today_light") { Shell(state(NookiTab.TODAY)) }
    @Test fun today_dark() = shot("today_dark", dark = true) { Shell(state(NookiTab.TODAY)) }
    @Test fun today_empty() = shot("today_empty") { Shell(state(NookiTab.TODAY, empty = true)) }
    @Test fun remember_light() = shot("remember_light") { Shell(state(NookiTab.REMEMBER)) }
    @Test fun remember_dark() = shot("remember_dark", dark = true) { Shell(state(NookiTab.REMEMBER)) }
    @Test fun nooki_light() = shot("nooki_light") { Shell(state(NookiTab.NOOKI)) }
    @Test fun nooki_dark() = shot("nooki_dark", dark = true) { Shell(state(NookiTab.NOOKI)) }

    @Test
    fun capture_hindi() = shot("capture_hindi") {
        CaptureContent(CaptureDraft(prefill = "कल सुबह 9 बजे डॉक्टर"), now, Persona.WORKING, emptyList(), {}, {}, requestFocus = false)
    }

    @Test
    fun capture_bill() = shot("capture_bill") {
        CaptureContent(CaptureDraft(prefill = "Pay rent on the 1st every month"), now, Persona.WORKING, emptyList(), {}, {}, requestFocus = false)
    }

    @Test
    fun onboarding_welcome() = shot("onboarding_welcome") {
        OnboardingScreen(Readiness(), ReadinessActions(), { _, _, _ -> })
    }

    @Test
    fun onboarding_noticing() = shot("onboarding_noticing") {
        OnboardingScreen(Readiness(noticingEnabled = false), ReadinessActions(), { _, _, _ -> }, initialStep = 4)
    }
}
