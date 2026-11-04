package com.example.attention

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/** Reads upcoming events when the person allows it. Nooki never writes to the calendar. */
class CalendarReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun upcoming(fromMillis: Long, toMillis: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis)
            ContentUris.appendId(it, toMillis)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY
        )
        val zone = TimeZone.getDefault()
        return runCatching {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val allDay = cursor.getInt(3) == 1
                        val rawBegin = cursor.getLong(2)
                        add(
                            CalendarEvent(
                                id = cursor.getLong(0),
                                title = cursor.getString(1).orEmpty().ifBlank { "Calendar event" },
                                // All-day events are stored at UTC midnight.
                                beginMillis = if (allDay) rawBegin - zone.getOffset(rawBegin) else rawBegin,
                                allDay = allDay
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
