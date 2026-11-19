package com.flixbox.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.flixbox.app.data.EpisodeModel
import com.flixbox.app.data.MediaModel
import com.flixbox.app.data.MediaRepository
import com.flixbox.app.ui.screens.*
import com.flixbox.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: MediaRepository

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* re-check on resume */ }

    private val readPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        repository = MediaRepository(this)
        checkPermissions()

        setContent {
            FlixBoxTheme {
                FlixBoxApp(repository = repository)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                } catch (e: Exception) {
                    manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                readPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────
sealed class Screen {
    object Home : Screen()
    object Search : Screen()
    object Settings : Screen()
    data class Detail(val media: MediaModel) : Screen()
    data class Player(
        val episodes: List<EpisodeModel>,
        val startIndex: Int,
        val sourceMedia: MediaModel
    ) : Screen()
}

@Composable
fun FlixBoxApp(repository: MediaRepository) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showAddScreen by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }

    var cachedMediaList by remember { mutableStateOf(listOf<MediaModel>()) }
    var isLoadingMedia by remember { mutableStateOf(true) }
    LaunchedEffect(refreshKey) {
        isLoadingMedia = true
        cachedMediaList = repository.getAllMedia()
        isLoadingMedia = false
    }

    val scope = rememberCoroutineScope()

    // Daftar episode gabungan semua season (atau flat kalau tanpa season) — sama persis
    // cara DetailScreen menyusun daftar buat tombol play biasa. Dipakai bareng oleh
    // Continue Watching (P2) dan pencarian episode (P4) biar tidak duplikat logic.
    suspend fun episodesFor(media: MediaModel): List<EpisodeModel> =
        if (media.hasSeasons) repository.getSeasons(media.folderPath).flatMap { it.episodes }
        else repository.getEpisodes(media.folderPath)

    // P2: tap kartu "Lanjutkan Nonton" → langsung ke Player di episode & posisi terakhir,
    // bukan cuma buka Detail lagi. Episode terakhir dicari dari getRecentlyWatched().
    val onContinueWatchingClick: (MediaModel) -> Unit = { media ->
        scope.launch {
            val lastPath = repository.getRecentlyWatched()
                .firstOrNull { (path, _) -> path.startsWith(media.folderPath) }
                ?.first
            val episodes = episodesFor(media)
            if (episodes.isNotEmpty()) {
                val idx = episodes.indexOfFirst { it.path == lastPath }.let { if (it >= 0) it else 0 }
                currentScreen = Screen.Player(episodes = episodes, startIndex = idx, sourceMedia = media)
            } else {
                // Episode terakhir sudah tidak ada (terhapus/dipindah) — fallback ke Detail
                currentScreen = Screen.Detail(media)
            }
        }
    }

    // P4: tap hasil pencarian episode → langsung ke Player di episode itu persis
    val onEpisodeSearchClick: (MediaModel, EpisodeModel) -> Unit = { media, episode ->
        scope.launch {
            val episodes = episodesFor(media)
            val idx = episodes.indexOfFirst { it.path == episode.path }.let { if (it >= 0) it else 0 }
            currentScreen = Screen.Player(episodes = episodes, startIndex = idx, sourceMedia = media)
        }
    }

    // ── BackHandler global ────────────────────────────────────────────────────
    BackHandler(enabled = true) {
        when {
            showAddScreen -> showAddScreen = false
            currentScreen is Screen.Player -> {
                val screen = currentScreen as Screen.Player
                currentScreen = Screen.Detail(screen.sourceMedia)
            }
            currentScreen is Screen.Detail -> {
                refreshKey++
                currentScreen = Screen.Home
            }
            currentScreen is Screen.Search -> {
                currentScreen = Screen.Home
            }
            currentScreen is Screen.Settings -> {
                currentScreen = Screen.Home
            }
            currentScreen is Screen.Home -> {
                // Di home → tanya konfirmasi keluar
                showExitDialog = true
            }
        }
    }

    // ── Exit confirmation dialog ──────────────────────────────────────────────
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = FxDark,
            title = { Text("Keluar FlixBox?", color = FxWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menutup aplikasi?", color = FxGray) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    // Exit app
                    (currentScreen as? Screen)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) {
                    Text("Keluar", color = FxRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Batal", color = FxGray)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(FxBlack)) {

        when (val screen = currentScreen) {
            is Screen.Home -> {
                key(refreshKey) {
                    HomeScreen(
                        repository = repository,
                        mediaList = cachedMediaList,
                        isLoading = isLoadingMedia,
                        onMediaClick = { media -> currentScreen = Screen.Detail(media) },
                        onContinueWatchingClick = onContinueWatchingClick,
                        onAddClick = { showAddScreen = true },
                        onSearchClick = { currentScreen = Screen.Search },
                        onSettingsClick = { currentScreen = Screen.Settings }
                    )
                }
            }

            is Screen.Settings -> {
                SettingsScreen(
                    repository = repository,
                    onBack = { currentScreen = Screen.Home }
                )
            }

            is Screen.Search -> {
                SearchScreen(
                    repository = repository,
                    allMedia = cachedMediaList,
                    onMediaClick = { media -> currentScreen = Screen.Detail(media) },
                    onEpisodeClick = onEpisodeSearchClick,
                    onBack = { currentScreen = Screen.Home }
                )
            }

            is Screen.Detail -> {
                DetailScreen(
                    media = screen.media,
                    repository = repository,
                    onBack = {
                        refreshKey++
                        currentScreen = Screen.Home
                    },
                    onPlayEpisode = { ep, eps ->
                        currentScreen = Screen.Player(
                            episodes = eps,
                            startIndex = eps.indexOf(ep),
                            sourceMedia = screen.media
                        )
                    },
                    onDelete = { media ->
                        repository.deleteFolder(media.folderPath)
                        refreshKey++
                        currentScreen = Screen.Home
                    }
                )
            }

            is Screen.Player -> {
                PlayerScreen(
                    episodes = screen.episodes,
                    startIndex = screen.startIndex,
                    repository = repository,
                    titleFolderPath = screen.sourceMedia.folderPath,
                    onBack = { currentScreen = Screen.Detail(screen.sourceMedia) }
                )
            }
        }

        if (showAddScreen) {
            AddMediaScreen(
                repository = repository,
                onDismiss = { showAddScreen = false },
                onDone = {
                    showAddScreen = false
                    refreshKey++
                }
            )
        }
    }
}
