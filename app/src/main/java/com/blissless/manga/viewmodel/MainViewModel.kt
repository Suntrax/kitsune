package com.blissless.manga.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blissless.manga.data.ChapterInfo
import com.blissless.manga.data.ChapterImages
import com.blissless.manga.data.HomeSection
import com.blissless.manga.data.MangaDetail
import com.blissless.manga.data.MangaRepository
import com.blissless.manga.data.MangaSearchResult
import com.blissless.manga.data.MangaTrack
import com.blissless.manga.data.ReadingStatus
import com.blissless.manga.data.TrackingManager
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
    
    private var currentMangaId: String? = null
    private var currentMangaTitle: String? = null
    private var currentMangaCoverUrl: String? = null
    private var currentMangaUrl: String? = null

    fun getCurrentMangaCoverUrl(): String? = currentMangaCoverUrl

    private fun log(tag: String, msg: String) {
        Log.d("ViewModel", "[$tag] $msg")
    }

    private fun countWholeChapters(chapters: List<ChapterInfo>): Int {
        var count = 0
        chapters.forEach { chapter ->
            val title = chapter.title ?: ""
            val hasDecimal = title.contains(".") && title.contains("Chapter")
            if (!hasDecimal) {
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
                onSuccess = { chapterList ->
                    val nextChapterIndex = firstManga.currentChapterIndex + 1
                    Log.d("PRELOAD", "Found ${chapterList.size} chapters, next: $nextChapterIndex")
                    val chapter = chapterList.getOrNull(nextChapterIndex)
                    if (chapter != null) {
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

    fun continueFromTracking(track: com.blissless.manga.data.MangaTrack, onReady: () -> Unit) {
        currentMangaId = track.mangaId
        currentMangaTitle = track.title
        currentMangaCoverUrl = track.coverUrl
        currentMangaUrl = track.mangaUrl
        _readChapterIndices.value = (0..track.currentChapterIndex).toSet()
        _nextChapterToRead.value = track.currentChapterIndex + 1
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
                onSuccess = { chapterList ->
                    _chapters.value = chapterList
                    log("CHAPTERS", "Loaded ${chapterList.size} chapters for continue reading")
                    val totalChapters = _mangaDetail.value?.totalChapterCount ?: chapterList.size
                    trackingManager.updateTotalChapters(track.mangaId, totalChapters)
                    refreshTrackingLists()
                    
                    val nextChapterIndex = track.currentChapterIndex + 1
                    val chapter = chapterList.getOrNull(nextChapterIndex)
                    if (chapter != null) {
                        _selectedChapterIndex.value = nextChapterIndex
                        _isChapterRead.value = false
                        _chapterImages.value = UiState.Loading
                        
                        loadChapterImages(chapter.url)
                        log("READY", "Calling onReady, detail cover: ${_mangaDetail.value?.coverUrl}")
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
        if (scrollPercent >= 0.9f && _selectedChapterIndex.value >= 0) {
            _isChapterRead.value = true
            _readChapterIndices.value = _readChapterIndices.value + _selectedChapterIndex.value
            currentMangaId?.let { mangaId ->
                val chapter = _chapters.value.getOrNull(_selectedChapterIndex.value)
                if (chapter != null) {
                    val existing = trackingManager.getMangaTracking(mangaId)
                    val chapterNumber = extractMainChapterNumber(chapter.title ?: "")
                    val totalChapters = _mangaDetail.value?.totalChapterCount ?: _chapters.value.size
                    if (existing == null) {
                        val track = MangaTrack(
                            mangaId = mangaId,
                            title = currentMangaTitle ?: "",
                            coverUrl = currentMangaCoverUrl,
                            currentChapterIndex = _selectedChapterIndex.value,
                            currentChapterNumber = chapterNumber,
                            currentChapterUrl = chapter.url,
                            totalChapters = totalChapters,
                            status = ReadingStatus.READING,
                            lastReadTimestamp = System.currentTimeMillis(),
                            mangaUrl = currentMangaUrl ?: "https://atsu.moe/manga/$mangaId"
                        )
                        trackingManager.updateTracking(track)
                        log("TRACK", "Created tracking for chapter ${_selectedChapterIndex.value}")
                    } else if (existing.currentChapterIndex != _selectedChapterIndex.value) {
                        trackingManager.updateChapterProgress(mangaId, _selectedChapterIndex.value, chapterNumber, chapter.url)
                        log("TRACK", "Updated to chapter ${_selectedChapterIndex.value}")
                    }
                    refreshTrackingLists()
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
                val totalChapters = _mangaDetail.value?.totalChapterCount ?: _chapters.value.size
                if (existing == null) {
                    val track = MangaTrack(
                        mangaId = mangaId,
                        title = currentMangaTitle ?: "",
                        coverUrl = currentMangaCoverUrl,
                        currentChapterIndex = _selectedChapterIndex.value,
                        currentChapterNumber = chapterNumber,
                        currentChapterUrl = chapter.url,
                        totalChapters = totalChapters,
                        status = ReadingStatus.READING,
                        lastReadTimestamp = System.currentTimeMillis(),
                        mangaUrl = currentMangaUrl ?: "https://atsu.moe/manga/$mangaId"
                    )
                    trackingManager.updateTracking(track)
                } else {
                    trackingManager.updateChapterProgress(mangaId, _selectedChapterIndex.value, chapterNumber, chapter.url)
                }
                refreshTrackingLists()
            }
        }
    }

    fun updateQuery(query: String) {
        _searchQuery.value = query
        if (query != lastSearchedQuery) {
            lastSearchedQuery = ""
        }
    }

    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return

        if (query == lastSearchedQuery && _searchResults.value is UiState.Success) {
            log("SEARCH", "Skipping duplicate search for: '$query'")
            return
        }

        log("SEARCH", "Searching: '$query'")
        lastSearchedQuery = query
        viewModelScope.launch {
            _searchResults.value = UiState.Loading
            val result = repository.search(query)
            _searchResults.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Search failed") }
            )
        }
    }

    fun selectManga(manga: MangaSearchResult) {
        log("SELECT", "Selected: ${manga.title}")
        val mangaId = manga.url.substringAfter("/manga/").substringBefore("?")
        currentMangaId = mangaId
        currentMangaTitle = manga.title
        currentMangaCoverUrl = manga.coverUrl
        currentMangaUrl = manga.url
        loadMangaDetails(manga.url, manga.coverUrl)
        loadReadChapters(mangaId)
    }

    private fun loadReadChapters(mangaId: String) {
        val tracking = trackingManager.getMangaTracking(mangaId)
        if (tracking != null) {
            _readChapterIndices.value = (0..tracking.currentChapterIndex).toSet()
            _nextChapterToRead.value = tracking.currentChapterIndex + 1
        } else {
            _readChapterIndices.value = emptySet()
            _nextChapterToRead.value = 0
        }
    }

    fun startReading() {
        _mangaDetail.value?.let { detail ->
            val mangaUrl = "https://atsu.moe/manga/${detail.id}"
            currentMangaId = detail.id
            currentMangaTitle = detail.title
            currentMangaCoverUrl = detail.coverUrl
            currentMangaUrl = mangaUrl
            loadChapters(mangaUrl)
        }
    }

    private fun loadMangaDetails(mangaUrl: String, searchCoverUrl: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _mangaDetail.value = null
            val result = repository.getMangaDetails(mangaUrl)
            result.fold(
                onSuccess = { mangaDetail ->
                    val detailWithCover = mangaDetail.copy(
                        coverUrl = searchCoverUrl ?: mangaDetail.coverUrl
                    )
                    _mangaDetail.value = detailWithCover
                    log("DETAILS", "Loaded: ${mangaDetail.title}")
                },
                onFailure = {
                    log("ERROR", "Failed: ${it.message}")
                }
            )
            _isLoading.value = false
        }
    }

    private fun loadChapters(mangaUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.getChapters(mangaUrl)
            result.fold(
                onSuccess = { chapterList ->
                    _chapters.value = chapterList
                    log("CHAPTERS", "Loaded ${chapterList.size} chapters")
                    val mangaId = mangaUrl.substringAfter("/manga/").substringBefore("?")
                    val totalChapters = _mangaDetail.value?.totalChapterCount ?: chapterList.size
                    log("CHAPTERS", "Total chapters from detail: ${_mangaDetail.value?.totalChapterCount}, using: $totalChapters")
                    trackingManager.updateTotalChapters(mangaId, totalChapters)
                    refreshTrackingLists()
                },
                onFailure = {
                    log("ERROR", "Failed: ${it.message}")
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
                if (tracking != null && index <= tracking.currentChapterIndex) {
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

    fun clearMangaDetail() {
        _mangaDetail.value = null
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
