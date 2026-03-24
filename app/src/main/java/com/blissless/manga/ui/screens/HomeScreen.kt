package com.blissless.manga.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.blissless.manga.data.MangaSearchResult
import com.blissless.manga.data.MangaTrack
import com.blissless.manga.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onMangaSelected: (MangaSearchResult) -> Unit,
    onContinueReading: (MangaTrack) -> Unit,
    onRemoveFromReading: (MangaTrack) -> Unit
) {
    val continueReading by viewModel.continueReading.collectAsState()
    val planningToRead by viewModel.planningToRead.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshTrackingLists()
        viewModel.preloadContinueReading()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (continueReading.isNotEmpty()) {
            item {
                TrackingSection(
                    title = "Continue Reading",
                    tracks = continueReading,
                    onTrackClick = { track ->
                        val manga = MangaSearchResult(
                            title = track.title,
                            url = track.mangaUrl,
                            coverUrl = track.coverUrl,
                            mangaId = track.mangaId
                        )
                        onMangaSelected(manga)
                    },
                    onPlayClick = { track ->
                        onContinueReading(track)
                    },
                    onRemoveClick = { track ->
                        onRemoveFromReading(track)
                    }
                )
            }
        }

        if (planningToRead.isNotEmpty()) {
            item {
                TrackingSection(
                    title = "Planning to Read",
                    tracks = planningToRead,
                    onTrackClick = { track ->
                        val manga = MangaSearchResult(
                            title = track.title,
                            url = track.mangaUrl,
                            coverUrl = track.coverUrl,
                            mangaId = track.mangaId
                        )
                        onMangaSelected(manga)
                    },
                    onPlayClick = { track ->
                        val manga = MangaSearchResult(
                            title = track.title,
                            url = track.mangaUrl,
                            coverUrl = track.coverUrl,
                            mangaId = track.mangaId
                        )
                        onMangaSelected(manga)
                    }
                )
            }
        }

        if (continueReading.isEmpty() && planningToRead.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start reading manga to see them here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackingSection(
    title: String,
    tracks: List<MangaTrack>,
    onTrackClick: (MangaTrack) -> Unit,
    onPlayClick: (MangaTrack) -> Unit,
    onRemoveClick: ((MangaTrack) -> Unit)? = null
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks) { track ->
                TrackingCard(
                    track = track,
                    onClick = { onTrackClick(track) },
                    onPlayClick = { onPlayClick(track) },
                    onRemoveClick = onRemoveClick?.let { { it(track) } }
                )
            }
        }
    }
}

@Composable
fun TrackingCard(
    track: MangaTrack,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onRemoveClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3a)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFF3a3a4a)),
                contentAlignment = Alignment.Center
            ) {
                if (track.coverUrl != null) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = track.title.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFa855f7),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onRemoveClick != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable(onClick = onRemoveClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove from Reading",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFa855f7).copy(alpha = 0.9f), CircleShape)
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Continue Reading",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ch. ${if (track.currentChapterNumber > 0) track.currentChapterNumber else track.currentChapterIndex + 1}/${track.totalChapters}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFa855f7)
                )
            }
        }
    }
}
