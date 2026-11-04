package com.example.parser

import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone

/**
 * An optional, smarter way to pull a reminder out of free text, such as on-device Gemini Nano via
 * AICore (PRD section 5.2, step 2). Implementations may throw or return null; the caller falls
 * back to [ReminderTextParser].
 */
interface TaskExtractor {
    /** True when the backing model is downloaded and ready on this device. */
    suspend fun isAvailable(): Boolean

    suspend fun extract(input: String, nowMillis: Long, timeZone: TimeZone): ParsedReminder?
}

/**
 * Entry point for quick-add parsing (PRD FR-1). Tries [extractor] when one is installed and ready,
 * and silently falls back to the regex [ReminderTextParser] when it is missing, slow, throws, or
 * returns something unusable (FR-1.4). It never throws.
 *
 * No AI extractor ships yet: Gemini Nano through AICore only runs on a handful of recent flagship
 * phones, so the regex path is what every user gets today.
 */
class SmartReminderParser(
    private val extractor: TaskExtractor? = null,
    private val extractorTimeoutMillis: Long = 350L
) {
    suspend fun parse(
        input: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): ParsedReminder {
        val fromModel = extractor?.let { model ->
            runCatching {
                withTimeoutOrNull(extractorTimeoutMillis) {
                    if (model.isAvailable()) model.extract(input, nowMillis, timeZone) else null
                }
            }.getOrNull()
        }
        if (fromModel != null && fromModel.title.isNotBlank()) return fromModel
        return ReminderTextParser.parse(input, nowMillis, timeZone)
    }
}
