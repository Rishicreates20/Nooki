package com.example.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.core.today.NookiMood
import com.example.core.today.TodayBoard
import com.example.data.local.database.AppDatabase
import com.example.data.today.loadTodayBoard
import com.example.ui.theme.CottonSurface
import com.example.ui.theme.IndigoMist
import com.example.ui.theme.Leaf
import com.example.ui.theme.LeafOnDark
import com.example.ui.theme.MarigoldOnDark
import com.example.ui.theme.NeelIndigo
import com.example.ui.theme.NightIndigo
import com.example.ui.theme.Sindoor
import com.example.ui.theme.SindoorOnDark
import com.example.ui.theme.WashedCotton

/** Home-screen widget: Nooki's mood and what needs attention. Tap to open Today. */
class NookiWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val board = runCatching { loadTodayBoard(AppDatabase.getInstance(context)) }.getOrNull()
        provideContent { Content(board) }
    }

    @androidx.compose.runtime.Composable
    private fun Content(board: TodayBoard?) {
        val text = ColorProvider(day = NeelIndigo, night = WashedCotton)
        val quiet = ColorProvider(day = IndigoMist, night = Color(0xFFB4B9DE))
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(day = CottonSurface, night = NightIndigo))
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Nooki", style = TextStyle(color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Text(moodLine(board), style = TextStyle(color = quiet, fontSize = 13.sp), maxLines = 2)
            Spacer(GlanceModifier.height(8.dp))
            if (board != null) {
                Row {
                    Count(board.missed.size, "missed", ColorProvider(day = Sindoor, night = SindoorOnDark))
                    Spacer(GlanceModifier.width(12.dp))
                    Count(board.forgot.size, "forgot", ColorProvider(day = Color(0xFFB86E00), night = MarigoldOnDark))
                    Spacer(GlanceModifier.width(12.dp))
                    Count(board.todo.size, "to do", ColorProvider(day = Leaf, night = LeafOnDark))
                }
                val next = board.ringing ?: board.missed.firstOrNull() ?: board.todo.firstOrNull()
                if (next != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("${next.line} · ${next.detail}", style = TextStyle(color = text, fontSize = 14.sp), maxLines = 1)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Count(n: Int, label: String, color: androidx.glance.unit.ColorProvider) {
        Column {
            Text("$n", style = TextStyle(color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            Text(label, style = TextStyle(color = color, fontSize = 12.sp))
        }
    }

    private fun moodLine(board: TodayBoard?): String = when (board?.mood) {
        null, NookiMood.SLEEPING -> "Napping. Nothing needs you."
        NookiMood.CONTENT -> "Keeping an eye on today."
        NookiMood.LOOKING_UP -> "Looked up. Something needs you."
        NookiMood.FLUSTERED -> "A few things piled up."
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { NookiWidget().updateAll(context) }
        }
    }
}

class NookiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NookiWidget()
}
