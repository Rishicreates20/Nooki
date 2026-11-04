package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.NookiGraph
import com.example.R
import com.example.attention.AttentionItem
import com.example.attention.TimeWords
import com.example.attention.TodayDigest
import com.example.scheduler.NookiSchedulerTelemetry

private val WidgetBackground = Color(0xFF12153A)
private val WidgetText = Color(0xFFEEF0FA)
private val WidgetMuted = Color(0xFFADB2DA)
private val WidgetKnot = Color(0xFFFFAE1A)

/** Nooki on the home screen: what needs you, what's next, and one tap to say something. */
class NookiWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val digest = runCatching { NookiGraph.get(context).digestNow(now) }.getOrDefault(TodayDigest())
        provideContent { WidgetBody(digest, now) }
    }
}

@Composable
private fun WidgetBody(digest: TodayDigest, nowMillis: Long) {
    val context = LocalContext.current
    val next = (digest.ringing + digest.toDo).filterIsInstance<AttentionItem.Reminder>().firstOrNull()
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_launcher_foreground),
                    contentDescription = "Nooki",
                    modifier = GlanceModifier.size(40.dp)
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = digest.headline,
                    maxLines = 2,
                    style = TextStyle(
                        color = ColorProvider(WidgetText),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (next != null) {
                Text(
                    text = next.reminder.title,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(WidgetText), fontSize = 14.sp)
                )
                if (!next.reminder.undated) {
                    Text(
                        text = TimeWords.moment(next.reminder.triggerAtMillis, nowMillis),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 12.sp)
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.clickable(
                    actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CAPTURE))
                )
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_tile_knot),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(WidgetKnot)),
                    modifier = GlanceModifier.size(16.dp)
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "Tell Nooki",
                    style = TextStyle(color = ColorProvider(WidgetKnot), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

class NookiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NookiWidget()
}

object WidgetRefresher {
    /** A widget that fails to redraw must never break a reminder, so this cannot throw. */
    suspend fun refresh(context: Context) {
        runCatching { NookiWidget().updateAll(context) }.onFailure {
            NookiSchedulerTelemetry.record("WidgetRefresher", "Widget refresh failed: ${it.message}", isWarning = true)
        }
    }
}
