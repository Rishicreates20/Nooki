package com.example.data.repository

import com.example.data.local.dao.AttentionDao
import com.example.data.local.entity.LooseEndEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoticedEntity
import com.example.data.local.entity.NoticedStatus
import com.example.data.local.entity.NoticedType
import com.example.notice.NoticedCandidate
import com.example.parser.LooseEndDraft
import com.example.parser.MemoryDraft
import kotlinx.coroutines.flow.Flow

/** Saved things, money and things out with people, and what Nooki noticed. */
class AttentionRepository(private val dao: AttentionDao) {

    val memories: Flow<List<MemoryEntity>> = dao.observeMemories()
    val looseEnds: Flow<List<LooseEndEntity>> = dao.observeLooseEnds()
    val noticed: Flow<List<NoticedEntity>> = dao.observeNewNoticed()

    fun caughtSince(sinceMillis: Long): Flow<Int> = dao.observeCaughtSince(sinceMillis)

    suspend fun saveMemory(draft: MemoryDraft): MemoryEntity {
        val memory = MemoryEntity(text = draft.text, subject = draft.subject, place = draft.place)
        return memory.copy(id = dao.upsertMemory(memory))
    }

    suspend fun restoreMemory(memory: MemoryEntity) {
        dao.upsertMemory(memory)
    }

    suspend fun setMemoryPinned(memory: MemoryEntity, pinned: Boolean) = dao.setMemoryPinned(memory.id, pinned)

    suspend fun deleteMemory(memory: MemoryEntity) = dao.deleteMemory(memory.id)

    suspend fun saveLooseEnd(draft: LooseEndDraft, rawInput: String): LooseEndEntity {
        val end = LooseEndEntity(
            direction = draft.direction,
            person = draft.person,
            what = draft.what,
            amountRupees = draft.amountRupees,
            rawInput = rawInput
        )
        return end.copy(id = dao.upsertLooseEnd(end))
    }

    suspend fun settleLooseEnd(end: LooseEndEntity, atMillis: Long) = dao.setLooseEndSettled(end.id, atMillis)

    suspend fun reopenLooseEnd(end: LooseEndEntity) = dao.setLooseEndSettled(end.id, null)

    suspend fun deleteLooseEnd(end: LooseEndEntity) = dao.deleteLooseEnd(end.id)

    suspend fun restoreLooseEnd(end: LooseEndEntity) {
        dao.upsertLooseEnd(end)
    }

    /**
     * A second call from the same person adds to the same line ("Mom called 3 times"). Once a call
     * or message was dealt with, a new one opens it again. A bill that was handled stays handled.
     */
    suspend fun ingest(candidate: NoticedCandidate) {
        val existing = dao.findNoticed(candidate.dedupeKey)
        val fresh = candidate.type == NoticedType.MISSED_CALL || candidate.type == NoticedType.UNREAD_MESSAGE
        when {
            existing == null -> dao.insertNoticed(candidate.toEntity())
            existing.status == NoticedStatus.NEW && fresh -> dao.updateNoticed(
                existing.copy(times = existing.times + 1, noticedAtMillis = candidate.noticedAtMillis)
            )
            existing.status != NoticedStatus.NEW && fresh -> dao.updateNoticed(
                existing.copy(
                    status = NoticedStatus.NEW,
                    times = 1,
                    noticedAtMillis = candidate.noticedAtMillis,
                    surfaceAtMillis = candidate.surfaceAtMillis,
                    resolvedAtMillis = null
                )
            )
        }
    }

    suspend fun messageOpened(dedupeKey: String) = dao.deleteOpenedMessage(dedupeKey)

    suspend fun setNoticedStatus(item: NoticedEntity, status: NoticedStatus, atMillis: Long) =
        dao.setNoticedStatus(item.id, status, atMillis)

    suspend fun restoreNoticed(item: NoticedEntity) = dao.updateNoticed(item)

    /** Summaries of things already dealt with are not kept for long. */
    suspend fun purgeOld(beforeMillis: Long) = dao.deleteResolvedBefore(beforeMillis)

    suspend fun deleteAllNoticed() = dao.deleteAllNoticed()

    private fun NoticedCandidate.toEntity() = NoticedEntity(
        type = type,
        title = title,
        detail = detail,
        person = person,
        amountRupees = amountRupees,
        dueAtMillis = dueAtMillis,
        appLabel = appLabel,
        packageName = packageName,
        dedupeKey = dedupeKey,
        noticedAtMillis = noticedAtMillis,
        surfaceAtMillis = surfaceAtMillis
    )
}
