package com.blissless.manga.data

data class MangaTrack(
    val mangaId: String,
    val title: String,
    val coverUrl: String?,
    val currentChapterIndex: Int,
    val currentChapterNumber: Int = 0,
    val currentChapterUrl: String,
    val totalChapters: Int,
    val status: ReadingStatus,
    val lastReadTimestamp: Long,
    val mangaUrl: String,
    val anilistMediaId: Int? = null
)

enum class ReadingStatus {
    READING,
    PLANNING,
    COMPLETED,
    ON_HOLD,
    DROPPED
}
