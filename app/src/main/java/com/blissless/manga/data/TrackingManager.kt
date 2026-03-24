package com.blissless.manga.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TrackingManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "kitsune_tracking"
        private const val KEY_TRACKING = "manga_tracking"
    }
    
    fun getAllTracking(): List<MangaTrack> {
        val json = prefs.getString(KEY_TRACKING, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<MangaTrack>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getContinueReading(): List<MangaTrack> {
        return getAllTracking()
            .filter { it.status == ReadingStatus.READING }
            .sortedByDescending { it.lastReadTimestamp }
    }
    
    fun getPlanningToRead(): List<MangaTrack> {
        return getAllTracking()
            .filter { it.status == ReadingStatus.PLANNING }
            .sortedByDescending { it.lastReadTimestamp }
    }
    
    fun getMangaTracking(mangaId: String): MangaTrack? {
        return getAllTracking().find { it.mangaId == mangaId }
    }
    
    fun updateTracking(track: MangaTrack) {
        val all = getAllTracking().toMutableList()
        val index = all.indexOfFirst { it.mangaId == track.mangaId }
        if (index >= 0) {
            all[index] = track
        } else {
            all.add(track)
        }
        saveAll(all)
    }
    
    fun updateChapterProgress(mangaId: String, chapterIndex: Int, chapterUrl: String) {
        val existing = getMangaTracking(mangaId)
        if (existing != null) {
            val updated = existing.copy(
                currentChapterIndex = chapterIndex,
                currentChapterUrl = chapterUrl,
                lastReadTimestamp = System.currentTimeMillis(),
                status = ReadingStatus.READING
            )
            updateTracking(updated)
        }
    }

    fun markAsReading(mangaId: String, title: String, coverUrl: String?, mangaUrl: String, totalChapters: Int) {
        val existing = getMangaTracking(mangaId)
        if (existing != null) {
            val updated = existing.copy(
                status = ReadingStatus.READING,
                lastReadTimestamp = System.currentTimeMillis()
            )
            updateTracking(updated)
        } else {
            val track = MangaTrack(
                mangaId = mangaId,
                title = title,
                coverUrl = coverUrl,
                currentChapterIndex = 0,
                currentChapterUrl = "",
                totalChapters = totalChapters,
                status = ReadingStatus.READING,
                lastReadTimestamp = System.currentTimeMillis(),
                mangaUrl = mangaUrl
            )
            updateTracking(track)
        }
    }

    fun updateChapterProgress(mangaId: String, chapterIndex: Int, chapterNumber: Int, chapterUrl: String) {
        val existing = getMangaTracking(mangaId)
        if (existing != null) {
            val updated = existing.copy(
                currentChapterIndex = chapterIndex,
                currentChapterNumber = chapterNumber,
                currentChapterUrl = chapterUrl,
                lastReadTimestamp = System.currentTimeMillis(),
                status = ReadingStatus.READING
            )
            updateTracking(updated)
        }
    }
    
    fun markAsPlanning(mangaId: String, title: String, coverUrl: String?, mangaUrl: String, totalChapters: Int) {
        val existing = getMangaTracking(mangaId)
        if (existing != null) {
            val updated = existing.copy(
                status = ReadingStatus.PLANNING,
                lastReadTimestamp = System.currentTimeMillis()
            )
            updateTracking(updated)
        } else {
            val track = MangaTrack(
                mangaId = mangaId,
                title = title,
                coverUrl = coverUrl,
                currentChapterIndex = 0,
                currentChapterUrl = "",
                totalChapters = totalChapters,
                status = ReadingStatus.PLANNING,
                lastReadTimestamp = System.currentTimeMillis(),
                mangaUrl = mangaUrl
            )
            updateTracking(track)
        }
    }
    
    fun markChapterComplete(mangaId: String) {
        val existing = getMangaTracking(mangaId) ?: return
        val updated = existing.copy(
            lastReadTimestamp = System.currentTimeMillis()
        )
        updateTracking(updated)
    }

    fun updateTotalChapters(mangaId: String, totalChapters: Int) {
        val existing = getMangaTracking(mangaId)
        if (existing != null) {
            val updated = existing.copy(totalChapters = totalChapters)
            updateTracking(updated)
        }
    }
    
    fun removeTracking(mangaId: String) {
        val all = getAllTracking().filter { it.mangaId != mangaId }
        saveAll(all)
    }
    
    private fun saveAll(tracks: List<MangaTrack>) {
        val json = gson.toJson(tracks)
        prefs.edit().putString(KEY_TRACKING, json).apply()
    }
}
