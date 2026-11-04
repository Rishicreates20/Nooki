package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something the person asked Nooki to hold on to: "passport is in the blue bag". There is no date
 * on it, it simply has to be findable months later.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** The sentence as the person said it. */
    @ColumnInfo(name = "text")
    val text: String,

    /** What is being held, when Nooki could tell: "passport". */
    @ColumnInfo(name = "subject")
    val subject: String = "",

    /** Where it is, when Nooki could tell: "the blue bag". */
    @ColumnInfo(name = "place")
    val place: String = "",

    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long = createdAtMillis
)

enum class LooseEndDirection {
    /** Someone has my money or my thing. */
    THEY_HAVE,

    /** I have someone else's money or thing. */
    I_HAVE
}

/** Money or a thing out with someone: "Rahul owes me 500", "lent charger to Aman". */
@Entity(tableName = "loose_ends")
data class LooseEndEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "direction")
    val direction: LooseEndDirection,

    @ColumnInfo(name = "person")
    val person: String,

    /** The thing, or the amount as written. */
    @ColumnInfo(name = "what")
    val what: String,

    /** Set when it is money, so Nooki can total it. */
    @ColumnInfo(name = "amount_rupees")
    val amountRupees: Long? = null,

    @ColumnInfo(name = "raw_input")
    val rawInput: String = "",

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "settled_at_millis")
    val settledAtMillis: Long? = null
) {
    val isOpen: Boolean get() = settledAtMillis == null
}

enum class NoticedType { MISSED_CALL, UNREAD_MESSAGE, BILL, DELIVERY, EVENT }

enum class NoticedStatus {
    NEW,

    /** The person dealt with it. */
    HANDLED,

    /** It became a reminder. */
    TRACKED,

    /** The person said it did not matter. */
    IGNORED
}

/**
 * Something Nooki picked out of a notification. This is a summary only: message text is never
 * stored here, and OTPs and other secrets are dropped before a row can exist.
 */
@Entity(
    tableName = "noticed",
    indices = [
        Index(value = ["dedupe_key"], unique = true),
        Index(value = ["status"])
    ]
)
data class NoticedEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "type")
    val type: NoticedType,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "detail")
    val detail: String = "",

    @ColumnInfo(name = "person")
    val person: String? = null,

    @ColumnInfo(name = "amount_rupees")
    val amountRupees: Long? = null,

    @ColumnInfo(name = "due_at_millis")
    val dueAtMillis: Long? = null,

    @ColumnInfo(name = "app_label")
    val appLabel: String = "",

    @ColumnInfo(name = "package_name")
    val packageName: String = "",

    @ColumnInfo(name = "dedupe_key")
    val dedupeKey: String,

    @ColumnInfo(name = "status")
    val status: NoticedStatus = NoticedStatus.NEW,

    /** How many times this came in: "Mom called 3 times". */
    @ColumnInfo(name = "times")
    val times: Int = 1,

    @ColumnInfo(name = "noticed_at_millis")
    val noticedAtMillis: Long,

    /** Stays hidden until then, which gives a message time to be read on its own. */
    @ColumnInfo(name = "surface_at_millis")
    val surfaceAtMillis: Long = noticedAtMillis,

    @ColumnInfo(name = "resolved_at_millis")
    val resolvedAtMillis: Long? = null
)
