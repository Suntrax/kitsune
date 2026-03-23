package com.blissless.manga

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blissless.manga.ui.components.BottomNavBar
import com.blissless.manga.ui.screens.ExploreScreen
import com.blissless.manga.ui.screens.HomeScreen
import com.blissless.manga.ui.screens.MangaDetailScreen
import com.blissless.manga.ui.screens.ReaderScreen
import com.blissless.manga.ui.screens.SearchScreen
import com.blissless.manga.ui.theme.MangaTheme
import com.blissless.manga.viewmodel.MainViewModel
import com.blissless.manga.viewmodel.MainViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MangaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KitsuneApp()
                }
            }
        }
    }
}

sealed class Screen {
    data object Detail : Screen()
    data object Reader : Screen()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitsuneApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(context.applicationContext)
    )

    var currentScreen by remember { mutableStateOf<Screen?>(null) }
    
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    var lastActivePage by remember { mutableIntStateOf(1) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 2) {
            keyboardController?.hide()
        }
        if (pagerState.currentPage == 2) {
            lastActivePage = 2
        }
    }
    val scope = rememberCoroutineScope()
    val currentNavRoute by remember {
        derivedStateOf {
            when (pagerState.currentPage) {
                0 -> "explore"
                1 -> "home"
                else -> "search"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0
        ) { page ->
            when (page) {
                0 -> ExploreScreen(
                    viewModel = viewModel,
                    onMangaSelected = { manga ->
                        viewModel.selectManga(manga)
                        currentScreen = Screen.Detail
                    }
                )
                1 -> HomeScreen(
                    viewModel = viewModel,
                    onMangaSelected = { manga ->
                        viewModel.selectManga(manga)
                        currentScreen = Screen.Detail
                    },
                    onContinueReading = { track ->
                        viewModel.continueFromTracking(track) {
                            currentScreen = Screen.Reader
                        }
                    },
                    onRemoveFromReading = { track ->
                        viewModel.removeFromReading(track.mangaId)
                    }
                )
                else -> SearchScreen(
                    viewModel = viewModel,
                    onMangaSelected = { manga ->
                        viewModel.selectManga(manga)
                        currentScreen = Screen.Detail
                    },
                    isActive = pagerState.currentPage == 2
                )
            }
        }

        if (currentScreen != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
            ) {
                when (currentScreen) {
                    is Screen.Detail -> {
                        MangaDetailScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = null
                                viewModel.clearMangaDetail()
                            },
                            onStartReading = {
                                viewModel.startReading()
                                currentScreen = Screen.Reader
                            }
                        )
                    }

                    is Screen.Reader -> {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            ReaderScreen(
                                viewModel = viewModel,
                                onBack = {
                                    currentScreen = Screen.Detail
                                    viewModel.clearSelection()
                                }
                            )
                        }
                    }

                    null -> {}
                }
            }
        }

        if (currentScreen == null) {
            BottomNavBar(
                currentRoute = currentNavRoute,
                onNavigate = { route ->
                    scope.launch {
                        when (route) {
                            "explore" -> pagerState.animateScrollToPage(0)
                            "home" -> pagerState.animateScrollToPage(1)
                            "search" -> pagerState.animateScrollToPage(2)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
