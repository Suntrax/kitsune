package com.blissless.manga.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blissless.manga.data.AniListManager
import com.blissless.manga.data.ChapterInfo
import com.blissless.manga.data.ChapterImages
import com.blissless.manga.data.HomeSection
import com.blissless.manga.data.MangaDetail
import com.blissless.manga.data.MangaRepository
import com.blissless.manga.data.MangaSearchResult
import com.blissless.manga.data.MangaTrack
import com.blissless.manga.data.ReadingStatus
import com.blissless.manga.data.SettingsManager
import com.blissless.manga.data.TrackingManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class MainViewModel(private val context: Context) : ViewModel() {

    private val repository = MangaRepository(context)
    private val trackingManager = TrackingManager(context)
    private val anilistManager = AniListManager(context)
    private val settingsManager = SettingsManager(context)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<MangaSearchResult>>>(UiState.Idle)
    val searchResults: StateFlow<UiState<List<MangaSearchResult>>> = _searchResults.asStateFlow()

    private val _homeSections = MutableStateFlow<List<HomeSection>>(emptyList())
    val homeSections: StateFlow<List<HomeSection>> = _homeSections.asStateFlow()

    private val _continueReading = MutableStateFlow<List<MangaTrack>>(emptyList())
    val continueReading: StateFlow<List<MangaTrack>> = _continueReading.asStateFlow()

    private val _planningToRead = MutableStateFlow<List<MangaTrack>>(emptyList())
    val planningToRead: StateFlow<List<MangaTrack>> = _planningToRead.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChapterInfo>>(emptyList())
    val chapters: StateFlow<List<ChapterInfo>> = _chapters.asStateFlow()

    private val _mangaDetail = MutableStateFlow<MangaDetail?>(null)
    val mangaDetail: StateFlow<MangaDetail?> = _mangaDetail.asStateFlow()

    private val _selectedChapterIndex = MutableStateFlow(-1)
    val selectedChapterIndex: StateFlow<Int> = _selectedChapterIndex.asStateFlow()

    private val _chapterImages = MutableStateFlow<UiState<ChapterImages>>(UiState.Idle)
    val chapterImages: StateFlow<UiState<ChapterImages>> = _chapterImages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isChapterRead = MutableStateFlow(false)
    val isChapterRead: StateFlow<Boolean> = _isChapterRead.asStateFlow()

    private val _readChapterIndices = MutableStateFlow<Set<Int>>(emptySet())
    val readChapterIndices: StateFlow<Set<Int>> = _readChapterIndices.asStateFlow()

    private val _nextChapterToRead = MutableStateFlow<Int?>(null)
    val nextChapterToRead: StateFlow<Int?> = _nextChapterToRead.asStateFlow()

    private val _isPreloadingNext = MutableStateFlow(false)
    val isPreloadingNext: StateFlow<Boolean> = _isPreloadingNext.asStateFlow()

    private val chapterImageCache = mutableMapOf<String, ChapterImages>()
    private var preloadedNextChapter: ChapterImages? = null
    private var lastSearchedQuery: String = ""
    private var lastLoadedMangaId: String? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    
    private var currentMangaId: String? = null
    private var currentMangaTitle: String? = null
    private var currentMangaCoverUrl: String? = null
    private var currentMangaUrl: String? = null

    // AniList state
    private val _anilistUsername = MutableStateFlow<String?>(null)
    val anilistUsername: StateFlow<String?> = _anilistUsername.asStateFlow()

    private val _isAniListSyncing = MutableStateFlow(false)
    val isAniListSyncing: StateFlow<Boolean> = _isAniListSyncing.asStateFlow()

    private val _anilistSyncThreshold = MutableStateFlow(settingsManager.getAniListSyncThreshold())
    val anilistSyncThreshold: StateFlow<Int> = _anilistSyncThreshold.asStateFlow()

    private val _showMergeDialog = MutableStateFlow(false)
    val showMergeDialog: StateFlow<Boolean> = _showMergeDialog.asStateFlow()

    private var pendingAnilistUpdate: Job? = null
    private var hasSyncedOnStart = false

    fun getCurrentMangaCoverUrl(): String? = currentMangaCoverUrl

    private fun clearCachesIfNeeded(newMangaId: String) {
        if (lastLoadedMangaId != null && lastLoadedMangaId != newMangaId) {
            Log.d("CACHE", "Switching manga, clearing old cache")
            chapterImageCache.clear()
            preloadedNextChapter = null
        }
        lastLoadedMangaId = newMangaId
    }

    private fun log(tag: String, msg: String) {
        Log.d("ViewModel", "[$tag] $msg")
    }

    private fun countWholeChapters(chapters: List<ChapterInfo>): Int {
        val decimalPattern = Regex("\\d+\\.\\d+")
        var count = 0
        chapters.forEach { chapter ->
            val title = chapter.title ?: ""
            if (!decimalPattern.containsMatchIn(title)) {
                val mainChapter = extractMainChapterNumber(title)
                if (mainChapter > 0) count++
            }
        }
        log("COUNT", "Whole chapters: $count / ${chapters.size}")
        return count
    }

    private fun extractMainChapterNumber(title: String): Int {
        val patterns = listOf(
            Regex("Chapter\\s*(\\d+)"),
            Regex("Ch\\s*\\.\\s*(\\d+)"),
            Regex("^(\\d+)"),
            Regex("(\\d+)(?:\\.\\d+)?")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                val numStr = match.groupValues[1]
                return numStr.toIntOrNull() ?: 0
            }
        }
        return 0
    }

    fun loadHomePage() {
        viewModelScope.launch {
            _isLoading.value = true
            refreshTrackingLists()
            preloadContinueReading()
            // Sync from AniList on app start (only once)
            if (!hasSyncedOnStart && anilistManager.isLoggedIn()) {
                hasSyncedOnStart = true
                syncAnilistManga()
            }
            val result = repository.getHomePage()
            result.fold(
                onSuccess = { sections ->
                    _homeSections.value = sections
                    log("HOME", "Loaded ${sections.size} sections")
                },
                onFailure = {
                    log("ERROR", "Failed to load home: ${it.message}")
                }
            )
            _isLoading.value = false
        }
    }
    
    fun preloadContinueReading() {
        Log.d("PRELOAD", "=== preloadContinueReading() START ===")
        val continueReading = trackingManager.getContinueReading()
        Log.d("PRELOAD", "Continue reading count: ${continueReading.size}")
        val firstManga = continueReading.firstOrNull() ?: run {
            Log.d("PRELOAD", "No manga in continue reading - returning")
            return
        }
        Log.d("PRELOAD", "First manga: ${firstManga.title}")
        Log.d("PRELOAD", "Current chapter index: ${firstManga.currentChapterIndex}")
        
        viewModelScope.launch {
            Log.d("PRELOAD", "Fetching chapters for: ${firstManga.mangaUrl}")
            val chaptersResult = repository.getChapters(firstManga.mangaUrl)
            chaptersResult.fold(
                onSuccess = { chapterData ->
                    val chapterList = chapterData.chapters
                    val savedIndex = firstManga.currentChapterIndex
                    val nextChapterIndex = if (savedIndex > 0) savedIndex + 1 else 0
                    Log.d("PRELOAD", "Found ${chapterList.size} chapters (DB: ${chapterData.dbChapters}, DBZ: ${chapterData.dbzChapters}), target: $nextChapterIndex (saved=$savedIndex)")
                    val chapter = chapterList.getOrNull(nextChapterIndex)
                    if (chapter != null) {
                        val cached = chapterImageCache[chapter.url]
                        if (cached != null) {
                            Log.d("PRELOAD", "Chapter already cached: ${chapter.url}")
                        } else {
                            Log.d("PRELOAD", "Preloading chapter: ${chapter.url}")
                            val imagesResult = repository.getChapterImages(chapter.url)
                            imagesResult.fold(
                                onSuccess = { images ->
                                    chapterImageCache[chapter.url] = images
                                    Log.d("PRELOAD", "SUCCESS! Cached ${images.images.size} images for ${chapter.url}")
                                    Log.d("PRELOAD", "Cache now has ${chapterImageCache.size} entries")
                                },
                                onFailure = { e ->
                                    Log.d("PRELOAD", "FAILED to fetch images: ${e.message}")
                                }
                            )
                        }
                    } else {
                        Log.d("PRELOAD", "No chapter at index $nextChapterIndex")
                    }
                },
                onFailure = { e ->
                    Log.d("PRELOAD", "FAILED to get chapters: ${e.message}")
                }
            )
        }
    }
    
    fun refreshTrackingLists() {
        _continueReading.value = trackingManager.getContinueReading()
        _planningToRead.value = trackingManager.getPlanningToRead()
    }
    
    fun addToPlanning(mangaId: String, title: String, coverUrl: String?, mangaUrl: String, totalChapters: Int) {
        trackingManager.markAsPlanning(mangaId, title, coverUrl, mangaUrl, totalChapters)
        refreshTrackingLists()
    }
    
    fun removeFromPlanning(mangaId: String) {
        trackingManager.removeTracking(mangaId)
        refreshTrackingLists()
    }

    fun removeFromReading(mangaId: String) {
        trackingManager.removeTracking(mangaId)
        refreshTrackingLists()
    }

    fun addToReading(mangaId: String, title: String, coverUrl: String?, mangaUrl: String, totalChapters: Int, resetProgress: Boolean = false) {
        trackingManager.markAsReading(mangaId, title, coverUrl, mangaUrl, totalChapters, resetProgress)
        refreshTrackingLists()
    }
    
    fun togglePlanning(mangaId: String, title: String, coverUrl: String?, mangaUrl: String, totalChapters: Int) {
        if (isInPlanning(mangaId)) {
            removeFromPlanning(mangaId)
        } else {
            addToPlanning(mangaId, title, coverUrl, mangaUrl, totalChapters)
        }
    }
    
    fun isInPlanning(mangaId: String): Boolean {
        return trackingManager.getMangaTracking(mangaId)?.status == com.blissless.manga.data.ReadingStatus.PLANNING
    }

    fun getMangaTracking(mangaId: String): com.blissless.manga.data.MangaTrack? {
        return trackingManager.getMangaTracking(mangaId)
    }

    fun updateTrackingStatus(mangaId: String, status: com.blissless.manga.data.ReadingStatus) {
        trackingManager.updateTrackingStatus(mangaId, status)
        refreshTrackingLists()
        // Send AniList status update
        val tracking = trackingManager.getMangaTracking(mangaId)
        if (tracking != null) {
            updateAnilistProgressNow(tracking)
        }
    }

    fun continueFromTracking(track: com.blissless.manga.data.MangaTrack, onReady: () -> Unit) {
        val mangaId = extractUniqueMangaId(track.mangaId, track.mangaUrl)
        currentMangaId = mangaId
        currentMangaTitle = track.title
        currentMangaCoverUrl = track.coverUrl
        currentMangaUrl = track.mangaUrl
        _selectedChapterIndex.value = -1
        _isLoading.value = true
        _chapterImages.value = UiState.Idle
        
        viewModelScope.launch {
            val detailResult = repository.getMangaDetails(track.mangaUrl)
            detailResult.fold(
                onSuccess = { mangaDetail ->
                    currentMangaCoverUrl = track.coverUrl ?: mangaDetail.coverUrl ?: currentMangaCoverUrl
                    val detailWithCover = mangaDetail.copy(
                        coverUrl = currentMangaCoverUrl
                    )
                    _mangaDetail.value = detailWithCover
                    log("DETAIL", "Loaded detail with cover: ${detailWithCover.coverUrl}, track cover: ${track.coverUrl}")
                },
                onFailure = {
                    log("ERROR", "Failed to load detail: ${it.message}")
                }
            )
            
            val result = repository.getChapters(track.mangaUrl)
            result.fold(
                onSuccess = { chapterData ->
                    val chapterList = chapterData.chapters
                    _chapters.value = chapterList
                    val totalChapters = countWholeChapters(chapterList)
                    log("CHAPTERS", "Loaded ${chapterList.size} chapters for continue reading, total: $totalChapters")
                    trackingManager.updateTotalChapters(mangaId, totalChapters) 
                    refreshTrackingLists()
                    
                    // Find position by chapter URL or number match
                    val savedUrl = track.currentChapterUrl
                    var currentPosition = -1
                    
                    // Try URL match
                    if (savedUrl.isNotBlank()) {
                        currentPosition = chapterList.indexOfFirst { it.url == savedUrl }
                        Log.d("CONTINUE", "URL match attempt: '$savedUrl', found index: $currentPosition")
                    }
                    
                    // If no URL match, try by saved chapter number
                    if (currentPosition < 0 && track.currentChapterNumber > 0) {
                        currentPosition = chapterList.indexOfFirst { ch -> 
                            ch.title?.contains(track.currentChapterNumber.toString()) == true
                        }
                        Log.d("CONTINUE", "Number match: ${track.currentChapterNumber}, found index: $currentPosition")
                    }
                    
                    // Last resort: use saved index but validate it
                    if (currentPosition < 0) {
                        currentPosition = track.currentChapterIndex.coerceIn(0, chapterList.lastIndex)
                        Log.d("CONTINUE", "Using fallback index: $currentPosition")
                    }
                    
                    val safeChapterIndex = currentPosition.coerceIn(0, chapterList.lastIndex)
                    val nextToRead = if (track.currentChapterNumber > 0) safeChapterIndex + 1 else 0
                    
                    _readChapterIndices.value = (0 until safeChapterIndex).toSet()
                    _nextChapterToRead.value = nextToRead + 1
                    Log.d("CONTINUE", "Final: current=$currentPosition safe=$safeChapterIndex next=$nextToRead, chapterNumber=${track.currentChapterNumber}")
                    
                    // Load the next chapter
                    val chapter = chapterList.getOrNull(nextToRead)
                    if (chapter != null) {
                        _selectedChapterIndex.value = nextToRead
                        _isChapterRead.value = safeChapterIndex > 0
                        _chapterImages.value = UiState.Loading
                        loadChapterImages(chapter.url)
                        log("READY", "Loading next chapter: ${chapter.title}")
                        onReady()
                    } else {
                        // At last chapter, load current
                        _selectedChapterIndex.value = safeChapterIndex
                        _chapterImages.value = UiState.Loading
                        loadChapterImages(chapterList[safeChapterIndex].url)
                    }
                },
                onFailure = {
                    log("ERROR", "Failed to load chapters: ${it.message}")
                    _isLoading.value = false
                }
            )
        }
    }

    fun continueFromCurrentManga(onReady: () -> Unit) {
        val mangaDetail = _mangaDetail.value ?: return
        val mangaUrl = currentMangaUrl ?: return
        val mangaId = currentMangaId ?: return

        _selectedChapterIndex.value = -1
        _isLoading.value = true
        _chapterImages.value = UiState.Idle
        
        val tracking = trackingManager.getMangaTracking(mangaId)
        val savedIndex = if (tracking?.status == com.blissless.manga.data.ReadingStatus.READING) {
            tracking.currentChapterIndex
        } else {
            0
        }
        val nextChapterIndex = if (savedIndex > 0) savedIndex + 1 else 0

        val chapterList = _chapters.value
        if (chapterList.isNotEmpty()) {
            val safeChapterIndex = nextChapterIndex.coerceIn(0, chapterList.lastIndex.coerceAtLeast(0))
            _readChapterIndices.value = (0 until safeChapterIndex).toSet()
            _nextChapterToRead.value = safeChapterIndex + 1
            
            val chapter = chapterList.getOrNull(safeChapterIndex)
            if (chapter != null) {
                _selectedChapterIndex.value = safeChapterIndex
                _isChapterRead.value = safeChapterIndex > 0
                loadChapterImages(chapter.url)
                onReady()
            }
            _isLoading.value = false
            return
        }
        
        viewModelScope.launch {
            val result = repository.getChapters(mangaUrl)
            result.fold(
                onSuccess = { chapterData ->
                    val chapterList = chapterData.chapters
                    _chapters.value = chapterList
                    
                    val safeChapterIndex = nextChapterIndex.coerceIn(0, chapterList.lastIndex.coerceAtLeast(0))
                    
                    _readChapterIndices.value = (0 until safeChapterIndex).toSet()
                    _nextChapterToRead.value = safeChapterIndex + 1
                    
                    val chapter = chapterList.getOrNull(safeChapterIndex)
                    if (chapter != null) {
                        _selectedChapterIndex.value = safeChapterIndex
                        _isChapterRead.value = safeChapterIndex > 0
                        loadChapterImages(chapter.url)
                        onReady()
                    }
                },
                onFailure = {
                    log("ERROR", "Failed to load chapters: ${it.message}")
                    _isLoading.value = false
                }
            )
        }
    }
    
    fun preloadNextChapter() {
        val nextIndex = _selectedChapterIndex.value + 1
        val nextChapter = _chapters.value.getOrNull(nextIndex) ?: return
        if (preloadedNextChapter != null) return
        
        _isPreloadingNext.value = true
        viewModelScope.launch {
            val result = repository.getChapterImages(nextChapter.url)
            result.fold(
                onSuccess = { images ->
                    preloadedNextChapter = images
                    log("PRELOAD", "Next chapter preloaded: ${images.images.size} images")
                },
                onFailure = {
                    log("PRELOAD", "Failed to preload next chapter")
                }
            )
            _isPreloadingNext.value = false
        }
    }
    
    fun getPreloadedNextChapter(): ChapterImages? {
        val images = preloadedNextChapter
        preloadedNextChapter = null
        return images
    }
    
    fun onChapterScrollProgress(scrollPercent: Float) {
        val threshold = _anilistSyncThreshold.value / 100f
        if (scrollPercent >= threshold && _selectedChapterIndex.value >= 0) {
            _isChapterRead.value = true
            _readChapterIndices.value = _readChapterIndices.value + _selectedChapterIndex.value
            currentMangaId?.let { mangaId ->
                val chapter = _chapters.value.getOrNull(_selectedChapterIndex.value)
                if (chapter != null) {
                    val existing = trackingManager.getMangaTracking(mangaId)
                    val chapterNumber = extractMainChapterNumber(chapter.title ?: "")
                    if (existing == null) {
                        val track = MangaTrack(
                            mangaId = mangaId,
                            title = currentMangaTitle ?: "",
                            coverUrl = currentMangaCoverUrl,
                            currentChapterIndex = _selectedChapterIndex.value,
                            currentChapterNumber = chapterNumber,
                            currentChapterUrl = chapter.url,
                            totalChapters = countWholeChapters(_chapters.value),
                            status = ReadingStatus.READING,
                            lastReadTimestamp = System.currentTimeMillis(),
                            mangaUrl = currentMangaUrl ?: "https://atsu.moe/manga/$mangaId"
                        )
                        trackingManager.updateTracking(track)
                        log("TRACK", "Created tracking for chapter ${_selectedChapterIndex.value}")
                    } else if (existing.currentChapterIndex != _selectedChapterIndex.value || existing.currentChapterNumber == 0) {
                        trackingManager.updateChapterProgress(mangaId, _selectedChapterIndex.value, chapterNumber, chapter.url)
                        log("TRACK", "Updated to chapter ${_selectedChapterIndex.value}")
                    }
                    refreshTrackingLists()
                    // Debounced AniList update
                    val tracking = trackingManager.getMangaTracking(mangaId)
                    if (tracking != null) {
                        scheduleAnilistProgressUpdate(tracking)
                    }
                }
            }
        }
    }
    
    fun updateCurrentChapter() {
        currentMangaId?.let { mangaId ->
            val chapter = _chapters.value.getOrNull(_selectedChapterIndex.value)
            if (chapter != null) {
                val existing = trackingManager.getMangaTracking(mangaId)
                val chapterNumber = extractMainChapterNumber(chapter.title ?: "")
                if (existing == null) {
                    val track = MangaTrack(
                        mangaId = mangaId,
                        title = currentMangaTitle ?: "",
                        coverUrl = currentMangaCoverUrl,
                        currentChapterIndex = _selectedChapterIndex.value,
                        currentChapterNumber = chapterNumber,
                        currentChapterUrl = chapter.url,
                        totalChapters = countWholeChapters(_chapters.value),
                        status = ReadingStatus.READING,
                        lastReadTimestamp = System.currentTimeMillis(),
                        mangaUrl = currentMangaUrl ?: "https://atsu.moe/manga/$mangaId"
                    )
                    trackingManager.updateTracking(track)
                } else if (existing.currentChapterIndex != _selectedChapterIndex.value || existing.currentChapterNumber == 0) {
                    trackingManager.updateChapterProgress(mangaId, _selectedChapterIndex.value, chapterNumber, chapter.url)
                }
                refreshTrackingLists()
            }
        }
    }

    private fun scheduleAnilistProgressUpdate(track: MangaTrack) {
        pendingAnilistUpdate?.cancel()
        pendingAnilistUpdate = viewModelScope.launch {
            delay(3000)
            updateAnilistProgressNow(track)
        }
    }

    private fun updateAnilistProgressNow(track: MangaTrack) {
        val mediaId = track.anilistMediaId ?: extractAnilistMediaId(track.mangaId) ?: return
        if (!anilistManager.isLoggedIn()) return
        val anilistStatus = when (track.status) {
            ReadingStatus.READING -> "CURRENT"
            ReadingStatus.PLANNING -> "PLANNING"
            ReadingStatus.COMPLETED -> "COMPLETED"
            ReadingStatus.ON_HOLD -> "PAUSED"
            ReadingStatus.DROPPED -> "DROPPED"
        }
        viewModelScope.launch {
            val result = anilistManager.updateMediaListEntry(mediaId, track.currentChapterNumber, anilistStatus)
            result.fold(
                onSuccess = {
                    Log.d("ANILIST", "Updated progress for media $mediaId to chapter ${track.currentChapterNumber}")
                },
                onFailure = { e ->
                    Log.e("ANILIST", "Failed to update progress: ${e.message}")
                }
            )
        }
    }

    private fun extractAnilistMediaId(mangaId: String): Int? {
        val prefix = "anilist_"
        return if (mangaId.startsWith(prefix)) {
            mangaId.substringAfter(prefix).toIntOrNull()
        } else null
    }

    fun updateQuery(query: String) {
        _searchQuery.value = query
        if (query != lastSearchedQuery) {
            lastSearchedQuery = ""
        }
    }

    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) {
            Log.d("SEARCH", "Empty query, skipping")
            return
        }

        Log.d("SEARCH", "search() called with: '$query', last searched: '$lastSearchedQuery'")
        
        if (query == lastSearchedQuery) {
            Log.d("SEARCH", "Same query as last, checking state...")
            if (_searchResults.value is UiState.Success) {
                Log.d("SEARCH", "Already have results for '$query', skipping")
                return
            }
        }

        Log.d("SEARCH", "Executing search for: '$query'")
        lastSearchedQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchResults.value = UiState.Loading
            Log.d("SEARCH", "Loading state set, about to call repository")
            val result = repository.search(query)
            if (_searchQuery.value != query) {
                Log.d("SEARCH", "Query changed during search ('$query' -> '${_searchQuery.value}'), ignoring results")
                return@launch
            }
            _searchResults.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Search failed") }
            )
            Log.d("SEARCH", "Results: ${(_searchResults.value as? UiState.Success)?.data?.size ?: 0} items")
        }
    }

    fun selectManga(manga: MangaSearchResult) {
        log("SELECT", "Selected: ${manga.title}, url: ${manga.url}")
        val mangaId = manga.mangaId ?: extractUniqueMangaId(
            manga.url.substringAfter("/manga/").substringBefore("?"),
            manga.url
        )
        clearCachesIfNeeded(mangaId)
        currentMangaId = mangaId
        currentMangaTitle = manga.title
        currentMangaCoverUrl = manga.coverUrl
        currentMangaUrl = manga.url
        loadMangaDetails(manga.url, manga.coverUrl)
        loadReadChapters(mangaId)
    }

    private fun extractUniqueMangaId(baseId: String, mangaUrl: String): String {
        val params = mangaUrl.substringAfter("?").split("&").filter { it.isNotBlank() }
        val versionParams = params.filter { it.startsWith("colored") || it.startsWith("uncolored") || it.startsWith("ver") }
        return if (versionParams.isNotEmpty()) {
            "$baseId?${versionParams.joinToString("&")}"
        } else {
            baseId
        }
    }

    private fun loadReadChapters(mangaId: String) {
        Log.d("LOAD", "loadReadChapters for: $mangaId")
        val tracking = trackingManager.getMangaTracking(mangaId)
        if (tracking != null) {
            Log.d("LOAD", "Found tracking: chapterIndex=${tracking.currentChapterIndex}")
            _readChapterIndices.value = (0..tracking.currentChapterIndex).toSet()
            _nextChapterToRead.value = tracking.currentChapterIndex + 1
        } else {
            Log.d("LOAD", "No tracking found - resetting")
            _readChapterIndices.value = emptySet()
            _nextChapterToRead.value = 0
        }
    }

    fun startReading() {
        _mangaDetail.value?.let { detail ->
            val baseMangaId = detail.id
            val mangaId = extractUniqueMangaId(baseMangaId, currentMangaUrl ?: "")
            currentMangaId = mangaId
            currentMangaTitle = detail.title
            currentMangaCoverUrl = detail.coverUrl
            val mangaUrl = currentMangaUrl ?: "https://atsu.moe/manga/$baseMangaId"
            
            // Check for saved progress - only if in READING status and valid
            val existingTracking = trackingManager.getMangaTracking(mangaId)
            val savedIndex = if (existingTracking?.status == com.blissless.manga.data.ReadingStatus.READING && 
                existingTracking.currentChapterIndex > 0) {
                existingTracking.currentChapterIndex
            } else {
                0
            }
            
            // Start new - always reset to beginning when pressing Start Reading
            _readChapterIndices.value = emptySet()
            _nextChapterToRead.value = 0
            _selectedChapterIndex.value = 0
            Log.d("START", "Starting fresh at chapter 0 (savedIndex was $savedIndex)")
            
            _isLoading.value = true  // Show loading screen
            
            addToReading(mangaId, detail.title, detail.coverUrl, mangaUrl, detail.totalChapterCount, resetProgress = true)
            loadChapters(mangaUrl)
        }
    }

    private fun loadMangaDetails(mangaUrl: String, searchCoverUrl: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _mangaDetail.value = null
            
            // Load both detail and chapters in parallel, use chapters to get correct total
            val detailDeferred = async { repository.getMangaDetails(mangaUrl) }
            val chaptersDeferred = async { repository.getChapters(mangaUrl) }
            
            detailDeferred.await().fold(
                onSuccess = { mangaDetail ->
                    val detailWithCover = mangaDetail.copy(
                        coverUrl = searchCoverUrl ?: mangaDetail.coverUrl
                    )
                    _mangaDetail.value = detailWithCover
                    log("DETAILS", "Loaded: ${mangaDetail.title}, raw chapters: ${mangaDetail.totalChapterCount}")
                },
                onFailure = {
                    log("ERROR", "Failed: ${it.message}")
                }
            )
            
            // Now update with real chapter count (excluding decimal sub-chapters)
            chaptersDeferred.await().fold(
                onSuccess = { chapterData ->
                    val total = countWholeChapters(chapterData.chapters)
                    _mangaDetail.value?.let { detail ->
                        _mangaDetail.value = detail.copy(totalChapterCount = total)
                    }
                    Log.d("DETAILS", "Updated chapter count to: $total")
                },
                onFailure = { }
            )
            
            _isLoading.value = false
        }
    }

    private fun loadChapters(mangaUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _selectedChapterIndex.value = -1
            _chapterImages.value = UiState.Idle
            Log.d("CHAPTERS", "Loading chapters for: $mangaUrl")
            val result = repository.getChapters(mangaUrl)
            result.fold(
                onSuccess = { chapterData ->
                    val chapterList = chapterData.chapters
                    _chapters.value = chapterList
                    
                    // Count only whole chapters (exclude decimal sub-chapters like 1.1, 1.2)
                    val totalChapters = countWholeChapters(chapterList)
                    Log.d("CHAPTERS", "Loaded ${chapterList.size} items, whole chapters: $totalChapters")
                    val baseMangaId = mangaUrl.substringAfter("/manga/").substringBefore("?")
                    val uniqueMangaId = extractUniqueMangaId(baseMangaId, mangaUrl)
                    Log.d("CHAPTERS", "Using mangaId: $uniqueMangaId")
                    clearCachesIfNeeded(uniqueMangaId)
                    Log.d("CHAPTERS", "Total chapters: $totalChapters")
                    
                    // Update detail with correct count
                    _mangaDetail.value?.let { detail ->
                        _mangaDetail.value = detail.copy(
                            totalChapterCount = totalChapters,
                            synopsis = "${detail.synopsis}\n\nOriginal: Dragon Ball (${chapterData.dbChapters}) | Dragon Ball Z (${chapterData.dbzChapters})"
                        )
                    }
                    
                    trackingManager.updateTotalChapters(uniqueMangaId, totalChapters)
                    refreshTrackingLists()
                    
                    // Use the index that was set in startReading/continue
                    val savedIndex = _selectedChapterIndex.value
                    
                    if (savedIndex > 0 && savedIndex < chapterList.size) {
                        Log.d("CHAPTERS", "Loading chapter: $savedIndex")
                        selectChapter(savedIndex)
                    } else if (savedIndex == 0) {
                        // Start from beginning (chapter 0)
                        Log.d("CHAPTERS", "Starting from chapter 0")
                        selectChapter(0)
                    }
                    // If -1, stay on chapter list
                },
                onFailure = {
                    Log.d("ERROR", "Failed: ${it.message}")
                }
            )
            _isLoading.value = false
        }
    }

    fun selectChapter(index: Int) {
        if (index >= 0 && index < _chapters.value.size) {
            _selectedChapterIndex.value = index
            _isChapterRead.value = false
            _isLoading.value = true
            val chapter = _chapters.value[index]
            currentMangaId?.let { mangaId ->
                val tracking = trackingManager.getMangaTracking(mangaId)
                if (tracking != null && index < tracking.currentChapterIndex) {
                    _isChapterRead.value = true
                }
            }
            log("SELECT", "Selected chapter $index: ${chapter.url}")
            
            val preloaded = getPreloadedNextChapter()
            if (preloaded != null && chapterImageCache[chapter.url] == null) {
                chapterImageCache[chapter.url] = preloaded
            }
            
            loadChapterImages(chapter.url)
            preloadNextChapter()
        }
    }

    private fun loadChapterImages(chapterUrl: String) {
        log("LOAD", "Attempting to load: $chapterUrl")
        log("LOAD", "Cache keys: ${chapterImageCache.keys}")
        chapterImageCache[chapterUrl]?.let { cached ->
            log("CACHE", "HIT! Using cached images for: $chapterUrl with ${cached.images.size} images")
            _chapterImages.value = UiState.Success(cached)
            _isLoading.value = false
            return
        }
        log("CACHE", "MISS! Not in cache, will fetch from network")

        viewModelScope.launch {
            _chapterImages.value = UiState.Loading
            val result = repository.getChapterImages(chapterUrl)
            result.fold(
                onSuccess = { images ->
                    chapterImageCache[chapterUrl] = images
                    log("CACHE", "Cached ${images.images.size} images for: $chapterUrl")
                    _chapterImages.value = UiState.Success(images)
                },
                onFailure = {
                    _chapterImages.value = UiState.Error(it.message ?: "Failed to load images")
                }
            )
            _isLoading.value = false
        }
    }

    fun goToNext() {
        val current = _selectedChapterIndex.value
        if (current < _chapters.value.size - 1) {
            selectChapter(current + 1)
        }
    }

    fun goToPrevious() {
        val current = _selectedChapterIndex.value
        if (current > 0) {
            selectChapter(current - 1)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = UiState.Idle
        lastSearchedQuery = ""
    }

    fun clearSelection() {
        _chapters.value = emptyList()
        _selectedChapterIndex.value = -1
        _chapterImages.value = UiState.Idle
    }
    
    fun showChapterList() {
        // Just show chapter list without auto-selecting
        _selectedChapterIndex.value = -1
        _chapterImages.value = UiState.Idle
        _isLoading.value = false
    }
    
    fun showChapterListOnly() {
        // Show chapter list without loading any chapter - but ensure chapters are loaded
        _selectedChapterIndex.value = -1
        _chapterImages.value = UiState.Idle
        
        // If chapters not loaded, load them
        if (_chapters.value.isEmpty()) {
            Log.d("CHAPTERS", "Chapters not loaded, loading for chapter list")
            currentMangaUrl?.let { url ->
                _isLoading.value = true
                loadChaptersForList()
            }
        } else {
            _isLoading.value = false
        }
    }
    
    private fun loadChaptersForList() {
        viewModelScope.launch {
            val result = repository.getChapters(currentMangaUrl!!)
            result.fold(
                onSuccess = { chapterData ->
                    _chapters.value = chapterData.chapters
                    _isLoading.value = false
                    Log.d("CHAPTERS", "Loaded ${chapterData.chapters.size} chapters for list")
                },
                onFailure = { 
                    _isLoading.value = false
                }
            )
        }
    }

    fun getAnilistAuthUrl(): String = anilistManager.getAuthUrl()

    fun isAnilistLoggedIn(): Boolean = anilistManager.isLoggedIn()

    fun handleAuthRedirect(intent: Intent?) {
        intent?.dataString?.takeIf { it.startsWith("animescraper://success") }?.let { uri ->
            Uri.parse(uri.replace("#", "?")).getQueryParameter("access_token")?.let { token ->
                anilistManager.saveAccessToken(token)
                viewModelScope.launch {
                    val nameResult = anilistManager.fetchAndCacheUserInfo()
                    nameResult.fold(
                        onSuccess = { name ->
                            _anilistUsername.value = name
                            val localTracks = trackingManager.getAllTracking()
                            if (localTracks.isNotEmpty()) {
                                _showMergeDialog.value = true
                            } else {
                                syncAnilistManga()
                            }
                        },
                        onFailure = { Log.e("ANILIST", "Failed to fetch user info") }
                    )
                }
            }
        }
    }

    fun syncAnilistManga() {
        viewModelScope.launch {
            _isAniListSyncing.value = true
            val result = anilistManager.getUserMangaLists()
            result.fold(
                onSuccess = { entries ->
                    Log.d("ANILIST", "Fetched ${entries.size} entries from AniList")
                },
                onFailure = { Log.e("ANILIST", "Sync failed: ${it.message}") }
            )
            // Match and create tracking entries outside fold to allow suspend calls
            val entries = anilistManager.getSyncedManga()
            val matched = mutableListOf<com.blissless.manga.data.AniListMangaEntry>()
            for (entry in entries) {
                if (entry.status in setOf("CURRENT", "PLANNING", "REPEATING")) {
                    if (entry.localMangaUrl == null) {
                        matched.add(matchWithSearch(entry))
                    } else if (entry.chapters == null) {
                        matched.add(fillMissingChapters(entry))
                    } else {
                        matched.add(entry)
                    }
                } else {
                    matched.add(entry)
                }
            }
            anilistManager.createTrackingEntries(matched, trackingManager)
            refreshTrackingLists()
            _anilistUsername.value = anilistManager.getLoggedInUser()
            Log.d("ANILIST", "Synced ${matched.size} manga entries")
            _isAniListSyncing.value = false
        }
    }

    fun overwriteAnilistWithLocal() {
        _showMergeDialog.value = false
        viewModelScope.launch {
            val localTracks = trackingManager.getAllTracking()
            for (track in localTracks) {
                val mediaId = track.anilistMediaId ?: extractAnilistMediaId(track.mangaId)
                if (mediaId != null && anilistManager.isLoggedIn()) {
                    val anilistStatus = when (track.status) {
                        ReadingStatus.READING -> "CURRENT"
                        ReadingStatus.PLANNING -> "PLANNING"
                        ReadingStatus.COMPLETED -> "COMPLETED"
                        ReadingStatus.ON_HOLD -> "PAUSED"
                        ReadingStatus.DROPPED -> "DROPPED"
                    }
                    anilistManager.updateMediaListEntry(mediaId, track.currentChapterNumber, anilistStatus)
                }
            }
        }
    }

    fun discardLocalAndSync() {
        _showMergeDialog.value = false
        for (track in trackingManager.getAllTracking()) {
            trackingManager.removeTracking(track.mangaId)
        }
        refreshTrackingLists()
        syncAnilistManga()
    }

    fun mergeLocalAndAnilist() {
        _showMergeDialog.value = false
        viewModelScope.launch {
            val result = anilistManager.getUserMangaLists()
            result.fold(
                onSuccess = { Log.d("ANILIST", "Fetched ${it.size} entries from AniList") },
                onFailure = { Log.e("ANILIST", "Sync failed: ${it.message}") }
            )
            val entries = anilistManager.getSyncedManga()
            val matched = mutableListOf<com.blissless.manga.data.AniListMangaEntry>()
            for (entry in entries) {
                val existing = trackingManager.getMangaTracking("anilist_${entry.mediaId}")
                if (existing != null) {
                    matched.add(entry)
                } else {
                    if (entry.status in setOf("CURRENT", "PLANNING", "REPEATING")) {
                        if (entry.localMangaUrl == null) {
                            matched.add(matchWithSearch(entry))
                        } else if (entry.chapters == null) {
                            matched.add(fillMissingChapters(entry))
                        } else {
                            matched.add(entry)
                        }
                    } else {
                        matched.add(entry)
                    }
                }
            }
            anilistManager.createTrackingEntries(matched, trackingManager)
            refreshTrackingLists()
            _anilistUsername.value = anilistManager.getLoggedInUser()
            _isAniListSyncing.value = false
        }
    }

    private suspend fun matchWithSearch(entry: com.blissless.manga.data.AniListMangaEntry): com.blissless.manga.data.AniListMangaEntry {
        val titlesToTry = listOfNotNull(entry.englishTitle, entry.title, entry.nativeTitle)
        for (title in titlesToTry) {
            try {
                val result = repository.search(title)
                result.getOrNull()?.let { results ->
                    val match = results.firstOrNull { r ->
                        r.title.equals(entry.title, ignoreCase = true) ||
                        r.title.equals(entry.englishTitle, ignoreCase = true) ||
                        entry.title.equals(r.title, ignoreCase = true)
                    }
                    if (match != null) {
                        var matchedEntry = entry.copy(localMangaUrl = match.url)
                        if (matchedEntry.chapters == null) {
                            val chaptersResult = repository.getChapters(match.url)
                            chaptersResult.fold(
                                onSuccess = { chapterData ->
                                    val total = countWholeChapters(chapterData.chapters)
                                    if (total > 0) {
                                        matchedEntry = matchedEntry.copy(chapters = total)
                                    }
                                },
                                onFailure = { }
                            )
                        }
                        anilistManager.updateSyncedMangaEntry(matchedEntry)
                        return matchedEntry
                    }
                }
            } catch (e: Exception) {
                Log.e("ANILIST", "Search failed for ${entry.title}: ${e.message}")
            }
        }
        return entry
    }

    private suspend fun fillMissingChapters(entry: com.blissless.manga.data.AniListMangaEntry): com.blissless.manga.data.AniListMangaEntry {
        val url = entry.localMangaUrl ?: return entry
        val chaptersResult = repository.getChapters(url)
        chaptersResult.fold(
            onSuccess = { chapterData ->
                val total = countWholeChapters(chapterData.chapters)
                if (total > 0) {
                    val updated = entry.copy(chapters = total)
                    anilistManager.updateSyncedMangaEntry(updated)
                    return updated
                }
            },
            onFailure = { }
        )
        return entry
    }

    fun getAnilistUsername(): String? = anilistManager.getLoggedInUser()

    fun logoutAniList() {
        val allTracks = trackingManager.getAllTracking()
        for (track in allTracks) {
            if (track.mangaId.startsWith("anilist_") || track.anilistMediaId != null) {
                trackingManager.removeTracking(track.mangaId)
            }
        }
        anilistManager.logout()
        _anilistUsername.value = null
        refreshTrackingLists()
    }

    fun checkAnilistSession() {
        if (anilistManager.isLoggedIn()) {
            _anilistUsername.value = anilistManager.getLoggedInUser()
        } else {
            _anilistUsername.value = null
        }
    }

    fun setManualChapterProgress(chapterNumber: Int) {
        val mangaId = currentMangaId ?: return
        val existing = trackingManager.getMangaTracking(mangaId)
        val chapterIndex = (chapterNumber - 1).coerceAtLeast(0)
        if (existing != null) {
            val updated = existing.copy(
                currentChapterIndex = chapterIndex,
                currentChapterNumber = chapterNumber,
                lastReadTimestamp = System.currentTimeMillis(),
                status = ReadingStatus.READING
            )
            trackingManager.updateTracking(updated)
            // Send to AniList immediately (no debounce for manual input)
            updateAnilistProgressNow(updated)
        } else {
            val track = MangaTrack(
                mangaId = mangaId,
                title = currentMangaTitle ?: (_mangaDetail.value?.title ?: ""),
                coverUrl = currentMangaCoverUrl,
                currentChapterIndex = chapterIndex,
                currentChapterNumber = chapterNumber,
                currentChapterUrl = "",
                totalChapters = countWholeChapters(_chapters.value).coerceAtLeast(_mangaDetail.value?.totalChapterCount ?: 0),
                status = ReadingStatus.READING,
                lastReadTimestamp = System.currentTimeMillis(),
                mangaUrl = currentMangaUrl ?: "https://atsu.moe/manga/$mangaId"
            )
            trackingManager.updateTracking(track)
            updateAnilistProgressNow(track)
        }
        refreshTrackingLists()
    }

    fun clearMangaDetail() {
        _mangaDetail.value = null
    }

    fun updateAnilistSyncThreshold(percent: Int) {
        settingsManager.setAniListSyncThreshold(percent)
        _anilistSyncThreshold.value = percent
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
