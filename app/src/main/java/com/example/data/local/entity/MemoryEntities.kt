package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** "Car parked at B2, pillar 14." "Spare key is with Sharma aunty." Search finds them in a second. */
@Entity(tableName = "saved_things", indices = [Index(value = ["thing"])])
data class SavedThingEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "thing") val thing: String,
    @ColumnInfo(name = "place") val place: String,
    @ColumnInfo(name = "raw_input") val rawInput: String = "",
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long = createdAtMillis,
)

/** Which way money or a thing went. Stored by name. */
enum class MoneyDirectionColumn { THEY_OWE_ME, I_OWE_THEM }

/** Money and things out there: who owes you, who you owe, what you lent, and since when. */
@Entity(tableName = "money_entries", indices = [Index(value = ["person"]), Index(value = ["settled_at_millis"])])
data class MoneyEntryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "person") val person: String,
    @ColumnInfo(name = "direction") val direction: MoneyDirectionColumn,
    /** Amount in paise, or null when it is a thing ("charger"). */
    @ColumnInfo(name = "amount_paise") val amountPaise: Long? = null,
    @ColumnInfo(name = "thing") val thing: String? = null,
    @ColumnInfo(name = "raw_input") val rawInput: String = "",
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "settled_at_millis") val settledAtMillis: Long? = null,
)

enum class NoticedType { MISSED_CALL, UNREAD_MESSAGE, BILL }

enum class NoticedStatus {
    /** Waiting for the person. Nothing becomes a reminder until they tap. */
    SUGGESTED,
    ACCEPTED,
    DISMISSED,
}

/**
 * Something Nooki noticed in a notification. Never message content: only who and when, or for a
 * bill only the name, amount and date. OTPs and balances are dropped before this is built.
 */
@Entity(
    tableName = "noticed_items",
    indices = [Index(value = ["status"]), Index(value = ["noticed_at_millis"]), Index(value = ["source_key_hash"])]
)
data class NoticedItemEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "type") val type: NoticedType,
    /** Caller, sender or biller name. */
    @ColumnInfo(name = "who") val who: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long? = null,
    @ColumnInfo(name = "due_day") val dueDay: Int? = null,
    @ColumnInfo(name = "due_month") val dueMonth: Int? = null,
    @ColumnInfo(name = "due_year") val dueYear: Int? = null,
    @ColumnInfo(name = "noticed_at_millis") val noticedAtMillis: Long,
    @ColumnInfo(name = "status") val status: NoticedStatus = NoticedStatus.SUGGESTED,
    /** One-way hash of the notification's key, to collapse updates and notice when it is opened. */
    @ColumnInfo(name = "source_key_hash") val sourceKeyHash: String? = null,
)
