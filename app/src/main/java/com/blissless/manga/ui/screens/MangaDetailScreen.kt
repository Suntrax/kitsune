package com.blissless.manga.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blissless.manga.data.ReadingStatus
import com.blissless.manga.viewmodel.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MangaDetailScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onStartReading: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenReaderDirect: () -> Unit,
    onOpenChapterSelect: () -> Unit
) {
    val mangaDetail by viewModel.mangaDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val detail = mangaDetail

    var currentStatus by remember(detail?.id) { mutableStateOf<ReadingStatus?>(null) }
    var currentChapter by remember(detail?.id) { mutableIntStateOf(1) }
    var showChapterSelect by remember(detail?.id) { mutableStateOf(false) }
    var fallbackCoverUrl by remember { mutableStateOf<String?>(null) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    fun refreshTracking() {
        detail?.id?.let { mangaId ->
            viewModel.refreshTrackingLists()
            val tracking = viewModel.getMangaTracking(mangaId)
            currentStatus = tracking?.status
            currentChapter = if (tracking != null && tracking.currentChapterNumber > 0) {
                tracking.currentChapterNumber
            } else {
                1
            }
            fallbackCoverUrl = tracking?.coverUrl ?: viewModel.getCurrentMangaCoverUrl()
        }
    }

    LaunchedEffect(detail?.id) { refreshTracking() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        refreshTracking()
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        when {
            isLoading && detail == null -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFa855f7))
                }
            }

            detail != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header with cover and banner
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Banner image
                        if (detail.bannerUrl != null) {
                            AsyncImage(
                                model = detail.bannerUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF1a1a2e)))
                        }

                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF121212).copy(alpha = 0.3f),
                                            Color(0xFF121212).copy(alpha = 0.8f),
                                            Color(0xFF121212)
                                        )
                                    )
                                )
                        )

                        // Cover and title overlay
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            AsyncImage(
                                model = fallbackCoverUrl ?: detail.coverUrl,
                                contentDescription = detail.title,
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = detail.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (detail.englishTitle != null && detail.englishTitle.isNotBlank() && detail.englishTitle != detail.title) {
                                    Text(
                                        text = detail.englishTitle,
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Meta info row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (detail.avgRating > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFfbbf24), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", detail.avgRating),
                                    color = Color(0xFFfbbf24),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${detail.totalChapterCount} chapters",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = detail.status,
                            color = if (detail.status == "Ongoing") Color(0xFF22c55e) else Color(0xFFa855f7),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Type, authors
                    if (detail.authors.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = detail.authors.joinToString(", "),
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = detail.type,
                                color = Color(0xFFa855f7),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Action buttons
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.continueFromCurrentManga { onOpenReaderDirect() }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFa855f7)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (currentStatus == ReadingStatus.READING) "Continue Ch $currentChapter" else "Start Reading",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { showChapterDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    showChapterSelect = !showChapterSelect
                                    if (showChapterSelect) {
                                        viewModel.showChapterListOnly()
                                        onOpenReader()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Chapters", fontWeight = FontWeight.Medium)
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showStatusMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        when (currentStatus) {
                                            ReadingStatus.READING -> "Reading"
                                            ReadingStatus.PLANNING -> "Plan to Read"
                                            ReadingStatus.COMPLETED -> "Completed"
                                            ReadingStatus.ON_HOLD -> "On Hold"
                                            ReadingStatus.DROPPED -> "Dropped"
                                            null -> "Add to List"
                                        },
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(
                                    expanded = showStatusMenu,
                                    onDismissRequest = { showStatusMenu = false }
                                ) {
                                    val statuses = listOf(
                                        ReadingStatus.READING,
                                        ReadingStatus.PLANNING,
                                        ReadingStatus.COMPLETED,
                                        ReadingStatus.ON_HOLD,
                                        ReadingStatus.DROPPED
                                    )
                                    statuses.forEach { status ->
                                        val label = when (status) {
                                            ReadingStatus.READING -> "Reading"
                                            ReadingStatus.PLANNING -> "Plan to Read"
                                            ReadingStatus.COMPLETED -> "Completed"
                                            ReadingStatus.ON_HOLD -> "On Hold"
                                            ReadingStatus.DROPPED -> "Dropped"
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (currentStatus == status) {
                                                        Icon(Icons.Default.Check, contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                            tint = Color(0xFFa855f7))
                                                        Spacer(Modifier.width(8.dp))
                                                    } else {
                                                        Spacer(Modifier.width(26.dp))
                                                    }
                                                    Text(label)
                                                }
                                            },
                                            onClick = {
                                                showStatusMenu = false
                                                if (currentStatus != status) {
                                                    viewModel.updateTrackingStatus(detail.id, status)
                                                    currentStatus = status
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chapter select row (when chapters loaded)
                    if (chapters.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Jump to Chapter", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            // Show a row of quick chapter buttons - current and surrounding
                            val totalCh = chapters.size
                            val maxVisible = minOf(totalCh, 7)
                            val startIdx = (currentChapter - 3).coerceAtLeast(0)
                            val endIdx = (startIdx + maxVisible).coerceAtMost(totalCh)
                            val visibleChapters = chapters.subList(startIdx, endIdx)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                visibleChapters.forEachIndexed { idx, ch ->
                                    val chNum = startIdx + idx + 1
                                    val isCurrent = chNum == currentChapter
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrent) Color(0xFFa855f7) else Color(0xFF2a2a3a))
                                            .clickable {
                                                viewModel.selectChapter(startIdx + idx)
                                                onOpenReaderDirect()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$chNum",
                                            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Genres
                    if (detail.genres.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Genres", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                detail.genres.forEach { genre ->
                                    SuggestionChip(
                                        onClick = { },
                                        label = { Text(genre, style = MaterialTheme.typography.bodySmall) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = Color(0xFF2a2a3a),
                                            labelColor = Color.White.copy(alpha = 0.85f)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Other names
                    if (detail.otherNames.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Also Known As", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = detail.otherNames.joinToString(" • "),
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Synopsis
                    if (detail.synopsis.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Synopsis", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = detail.synopsis,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f
                            )
                        }
                    }

                    Spacer(Modifier.height(80.dp))
                }
            }

            else -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Failed to load manga details", color = Color.White)
                }
            }
        }

        if (showChapterDialog) {
            ChapterProgressDialog(
                currentChapter = currentChapter,
                totalChapters = detail?.totalChapterCount ?: 0,
                onSet = { chapter ->
                    viewModel.setManualChapterProgress(chapter)
                    currentChapter = chapter + 1
                    showChapterDialog = false
                },
                onDismiss = { showChapterDialog = false }
            )
        }
    }
}

@Composable
fun ChapterProgressDialog(
    currentChapter: Int,
    totalChapters: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(currentChapter.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        title = {
            Text("Set Chapter Progress", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Enter the chapter you're currently on (1-$totalChapters):",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFa855f7),
                        focusedBorderColor = Color(0xFFa855f7),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chapter = input.toIntOrNull()
                    if (chapter != null && chapter in 1..totalChapters.coerceAtLeast(1)) {
                        onSet(chapter)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFa855f7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
