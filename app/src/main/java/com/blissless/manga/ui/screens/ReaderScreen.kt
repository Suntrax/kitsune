package com.blissless.manga.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blissless.manga.data.ChapterInfo
import com.blissless.manga.viewmodel.MainViewModel
import com.blissless.manga.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val chapters by viewModel.chapters.collectAsState()
    val selectedIndex by viewModel.selectedChapterIndex.collectAsState()
    val chapterImages by viewModel.chapterImages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isChapterRead by viewModel.isChapterRead.collectAsState()
    val readChapterIndices by viewModel.readChapterIndices.collectAsState()
    val nextChapterToRead by viewModel.nextChapterToRead.collectAsState()

    val listState = rememberLazyListState()
    var isShowingChapterList by remember { mutableStateOf(selectedIndex < 0) }
    var scrollProgress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(selectedIndex) {
        isShowingChapterList = selectedIndex < 0
    }
    
    LaunchedEffect(chapterImages) {
        if (chapterImages is UiState.Success && !isShowingChapterList) {
            viewModel.preloadNextChapter()
        }
    }
    
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@snapshotFlow 0f
            
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val progress = if (totalItems <= 1) 1f 
                else (firstVisibleItem.toFloat() + lastVisibleItem.toFloat()) / (2f * (totalItems - 1).toFloat())
            progress.coerceIn(0f, 1f)
        }.collect { progress: Float ->
            scrollProgress = progress
            viewModel.onChapterScrollProgress(progress)
        }
    }
    
    BackHandler {
        if (isShowingChapterList || selectedIndex < 0) {
            onBack()
        } else {
            isShowingChapterList = true
            viewModel.selectChapter(-1)
        }
    }

    Scaffold(
        modifier = Modifier.background(Color.Black),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Kitsune Reader",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedIndex >= 0 && selectedIndex < chapters.size) {
                                Text(
                                    text = chapters[selectedIndex].title ?: "Chapter ${selectedIndex + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (!isShowingChapterList && selectedIndex >= 0) {
                            IconButton(
                                onClick = { 
                                    if (selectedIndex > 0) {
                                        viewModel.selectChapter(selectedIndex - 1)
                                    }
                                },
                                enabled = selectedIndex > 0
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious, 
                                    contentDescription = "Previous Chapter", 
                                    tint = if (selectedIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                onClick = { 
                                    if (selectedIndex < chapters.size - 1) {
                                        viewModel.selectChapter(selectedIndex + 1)
                                    }
                                },
                                enabled = selectedIndex < chapters.size - 1
                            ) {
                                Icon(
                                    Icons.Default.SkipNext, 
                                    contentDescription = "Next Chapter", 
                                    tint = if (selectedIndex < chapters.size - 1) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.95f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                if (!isShowingChapterList && selectedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(scrollProgress)
                                .height(3.dp)
                                .background(Color(0xFFa855f7))
                        )
                        if (!isChapterRead) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(Color.Transparent)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(30.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFF22c55e).copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFa855f7))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading...", color = Color.White)
                    }
                }
            }

            isShowingChapterList || selectedIndex < 0 -> {
                ChapterListWithGroups(
                    chapters = chapters,
                    selectedIndex = selectedIndex,
                    readChapterIndices = readChapterIndices,
                    nextChapterToRead = nextChapterToRead,
                    onChapterClick = { 
                        viewModel.selectChapter(it)
                        isShowingChapterList = false
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            chapterImages is UiState.Success -> {
                val images = (chapterImages as UiState.Success).data.images
                if (images.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No images found", color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.selectChapter(selectedIndex) }) {
                                Text("Retry")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(images) { index, imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }

            chapterImages is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Error: ${(chapterImages as UiState.Error).message}",
                            color = Color.Red
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.selectChapter(selectedIndex) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                ChapterListWithGroups(
                    chapters = chapters,
                    selectedIndex = selectedIndex,
                    readChapterIndices = readChapterIndices,
                    nextChapterToRead = nextChapterToRead,
                    onChapterClick = { 
                        viewModel.selectChapter(it)
                        isShowingChapterList = false
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun ChapterListWithGroups(
    chapters: List<ChapterInfo>,
    selectedIndex: Int,
    readChapterIndices: Set<Int>,
    nextChapterToRead: Int?,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chapters.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No chapters found", color = Color.White)
        }
    } else {
        val groupedChapters = groupChaptersByMainChapter(chapters)
        val listState = rememberLazyListState()
        
        val chapterToExpand = if (selectedIndex >= 0) selectedIndex else nextChapterToRead
        
        LaunchedEffect(chapters, chapterToExpand) {
            if (chapterToExpand != null && chapterToExpand >= 0) {
                val targetGroupIndex = groupedChapters.indexOfFirst { (_, groupList) ->
                    groupList.any { it.first == chapterToExpand }
                }
                if (targetGroupIndex >= 0) {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(maxOf(0, targetGroupIndex))
                }
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupedChapters.forEachIndexed { index, (groupKey, groupList) ->
                val containsTarget = groupList.any { it.first == chapterToExpand }
                item(key = "chapter_group_$index") {
                    ChapterGroup(
                        groupKey = groupKey,
                        groupChapters = groupList,
                        selectedIndex = selectedIndex,
                        readChapterIndices = readChapterIndices,
                        nextChapterToRead = nextChapterToRead,
                        initiallyExpanded = containsTarget,
                        onChapterClick = onChapterClick
                    )
                }
            }
        }
    }
}

private fun groupChaptersByMainChapter(chapters: List<ChapterInfo>): List<Pair<String, List<Pair<Int, ChapterInfo>>>> {
    val indexedChapters = chapters.mapIndexed { index, chapter -> index to chapter }
    
    val byMainChapter = indexedChapters.groupBy { (_, chapter) ->
        val title = chapter.title ?: ""
        extractMainChapterNumber(title)
    }.toSortedMap()
    
    val result = mutableListOf<Pair<String, List<Pair<Int, ChapterInfo>>>>()
    val hasChapterZero = byMainChapter.containsKey(0)
    
    byMainChapter.forEach { (mainChapter, items) ->
        val rangeStart = if (hasChapterZero && mainChapter == 0) {
            0
        } else if (hasChapterZero) {
            ((mainChapter - 1) / 20) * 20 + 1
        } else {
            ((mainChapter - 1) / 20) * 20 + 1
        }
        
        val existingGroup = result.find { (key, _) -> 
            val existingRangeStart = key.substringAfter("Ch. ").substringBefore(" - ").toIntOrNull() ?: -1
            existingRangeStart == rangeStart
        }
        
        if (existingGroup != null) {
            val (existingKey, existingItems) = existingGroup
            val index = result.indexOf(existingGroup)
            val updatedItems = existingItems + items
            val maxChapter = updatedItems.maxOfOrNull { extractMainChapterNumber(it.second.title ?: "") } ?: rangeStart
            val displayStart = existingKey.substringAfter("Ch. ").substringBefore(" - ").toIntOrNull() ?: rangeStart
            val displayEnd = if (displayStart == 0) maxChapter.coerceAtMost(20) else maxChapter
            val displayKey = "Ch. $displayStart - $displayEnd"
            result[index] = displayKey to updatedItems
        } else {
            val displayStart = if (hasChapterZero && mainChapter == 0) 0 else rangeStart
            val displayEnd = if (displayStart == 0) 0 else rangeStart + 19
            val displayKey = "Ch. $displayStart - $displayEnd"
            result.add(displayKey to items)
        }
    }
    
    return result.sortedBy { 
        it.first.substringAfter("Ch. ").substringBefore(" - ").toIntOrNull() ?: 0 
    }
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

@Composable
fun ChapterGroup(
    groupKey: String,
    groupChapters: List<Pair<Int, ChapterInfo>>,
    selectedIndex: Int,
    readChapterIndices: Set<Int>,
    nextChapterToRead: Int?,
    initiallyExpanded: Boolean = false,
    onChapterClick: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) {
            expanded = true
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2a2a3a)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.background(Color(0xFF2a2a3a))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2a2a3a))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupKey,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${groupChapters.size} chapters",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .background(Color(0xFF2a2a3a))
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupChapters.forEach { (absoluteIndex, chapter) ->
                        ChapterRow(
                            chapter = chapter,
                            isSelected = absoluteIndex == selectedIndex,
                            isRead = absoluteIndex in readChapterIndices,
                            isNextToRead = absoluteIndex == nextChapterToRead,
                            onClick = { onChapterClick(absoluteIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterRow(
    chapter: ChapterInfo,
    isSelected: Boolean,
    isRead: Boolean,
    isNextToRead: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = when {
        isSelected -> Color(0xFFa855f7).copy(alpha = 0.25f)
        isRead -> Color(0xFF22c55e).copy(alpha = 0.15f)
        isNextToRead -> Color(0xFFa855f7).copy(alpha = 0.1f)
        else -> Color(0xFF1a1a1a)
    }
    val borderColor = when {
        isSelected -> Color(0xFFa855f7)
        isNextToRead -> Color(0xFFa855f7).copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .then(
                    if (isSelected || isNextToRead) Modifier
                        .border(2.dp, borderColor, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                    else Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRead && !isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Read",
                    tint = Color(0xFF22c55e),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reading",
                    tint = Color(0xFFa855f7),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (isNextToRead) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Next to Read",
                    tint = Color(0xFFa855f7),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = chapter.title ?: "Chapter",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isSelected -> Color(0xFFa855f7)
                    isNextToRead -> Color.White
                    isRead -> Color.White.copy(alpha = 0.7f)
                    else -> Color.White
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isSelected) {
                Text(
                    text = "Reading",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFa855f7)
                )
            } else if (isNextToRead && !isRead) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFa855f7)
                )
            }
        }
    }
}
