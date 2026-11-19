package com.flixbox.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.flixbox.app.data.EpisodeModel
import com.flixbox.app.data.MediaRepository
import com.flixbox.app.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

// Satu pilihan track audio untuk ditampilkan di dialog Subtitle & Audio (P3)
private data class AudioTrackChoice(
    val group: Tracks.Group,
    val trackIndexInGroup: Int,
    val label: String,
    val isSelected: Boolean
)

@Composable
fun PlayerScreen(
    episodes: List<EpisodeModel>,
    startIndex: Int,
    repository: MediaRepository,
    titleFolderPath: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var currentIndex by remember { mutableStateOf(startIndex) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isRepeat by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var sleepMinutesLeft by remember { mutableStateOf(0) }
    var seekingTo by remember { mutableStateOf<Long?>(null) }
    var gestureLabel by remember { mutableStateOf("") }
    var showGestureHint by remember { mutableStateOf(false) }
    var currentVolume by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    var currentBrightness by remember {
        mutableStateOf(try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) { 128 })
    }

    // P3: subtitle & audio track
    var availableSubtitles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedSubtitle by remember { mutableStateOf<File?>(null) }
    var subtitleDelayMs by remember { mutableStateOf(0L) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackChoice>>(emptyList()) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var showSubtitlePickerDialog by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(context).build() }

    // Bangun MediaItem episode, sekalian lampirkan subtitle (digeser dulu timing-nya
    // kalau ada delay) kalau ada yang dipilih/kedetect.
    fun buildMediaItem(ep: EpisodeModel, subtitle: File?, delayMs: Long): MediaItem {
        val builder = MediaItem.Builder().setUri(Uri.parse(ep.path))
        if (subtitle != null) {
            val fileToUse = repository.prepareSubtitleForPlayback(subtitle, delayMs)
            val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(fileToUse))
                .setMimeType(repository.subtitleMimeType(subtitle))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(config))
        }
        return builder.build()
    }

    // Ganti subtitle/delay TANPA reload episode dari awal — posisi tonton yang sedang
    // berjalan dipertahankan persis, tidak balik ke posisi tersimpan terakhir.
    fun applySubtitle(file: File?, delayMs: Long) {
        val ep = episodes.getOrNull(currentIndex) ?: return
        val resumeAt = player.currentPosition
        val wasPlaying = isPlaying
        player.setMediaItem(buildMediaItem(ep, file, delayMs), resumeAt)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    fun selectAudioTrack(choice: AudioTrackChoice) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndexInGroup))
            .build()
    }

    // ── Load episode ──────────────────────────────────────────────────────────
    // Dipanggil manual — tidak lewat LaunchedEffect(currentIndex)
    fun loadEpisode(index: Int) {
        if (index < 0 || index >= episodes.size) return
        val ep = episodes[index]
        repository.saveRecentlyWatched(ep.path)
        val saved = repository.getWatchedPosition(ep.path)

        // P3: cari subtitle yang namanya cocok di folder yang sama; kalau ketemu lebih
        // dari satu (beda bahasa), pilih yang pertama dulu, bisa diganti lewat dialog
        // Subtitle & Audio. Delay disimpan per file subtitle, bukan per episode, supaya
        // kalau subtitle-nya diganti manual, delay lama tidak ikut kebawa.
        availableSubtitles = repository.findSubtitlesForEpisode(ep.path)
        selectedSubtitle = availableSubtitles.firstOrNull()
        subtitleDelayMs = selectedSubtitle?.let { repository.getSubtitleDelayMs(it.path) } ?: 0L

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(buildMediaItem(ep, selectedSubtitle, subtitleDelayMs))
        player.prepare()
        if (saved > 5_000L && ep.duration > 0 && saved < ep.duration - 5_000L) {
            player.seekTo(saved)
        }
        player.playWhenReady = true
        player.setPlaybackSpeed(playbackSpeed)
    }

    // Pindah episode — simpan posisi lama dulu
    fun goToEpisode(newIndex: Int) {
        if (newIndex < 0 || newIndex >= episodes.size) return
        episodes.getOrNull(currentIndex)?.let {
            repository.saveWatchedPosition(it.path, player.currentPosition, it.duration, titleFolderPath)
        }
        currentIndex = newIndex
        loadEpisode(newIndex)
    }

    // Load episode pertama saat masuk player
    LaunchedEffect(Unit) {
        loadEpisode(startIndex)
    }

    // Landscape + keep screen on
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Player listener ───────────────────────────────────────────────────────
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
                    .flatMapIndexed { gi, group ->
                        (0 until group.length).map { ti ->
                            val format = group.getTrackFormat(ti)
                            AudioTrackChoice(
                                group = group,
                                trackIndexInGroup = ti,
                                label = format.language ?: format.label ?: "Track ${gi + 1}.${ti + 1}",
                                isSelected = group.isTrackSelected(ti)
                            )
                        }
                    }
            }
            override fun onPlaybackStateChanged(state: Int) {
                // Hanya auto-next dari listener, BUKAN dari tombol
                // Tombol next memanggil goToEpisode() sendiri — tidak lewat sini
                if (state == Player.STATE_ENDED) {
                    when {
                        isRepeat -> {
                            player.seekTo(0L)
                            player.play()
                        }
                        currentIndex < episodes.size - 1 -> {
                            // Tandai episode ini selesai, lalu pindah
                            episodes.getOrNull(currentIndex)?.let {
                                repository.saveWatchedPosition(it.path, it.duration, it.duration, titleFolderPath)
                            }
                            // Update currentIndex langsung tanpa trigger recompose loadEpisode
                            val nextIndex = currentIndex + 1
                            currentIndex = nextIndex
                            loadEpisode(nextIndex)
                        }
                        // Episode terakhir selesai — biarkan saja
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            episodes.getOrNull(currentIndex)?.let {
                repository.saveWatchedPosition(it.path, player.currentPosition, it.duration, titleFolderPath)
            }
            player.release()
        }
    }

    // Progress updater
    LaunchedEffect(player) {
        while (true) {
            if (seekingTo == null) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0L } ?: 0L
            }
            delay(500)
        }
    }

    // Simpan posisi tontonan berkala (P2). Sebelumnya posisi cuma disimpan saat pindah
    // episode atau keluar player (onDispose) — kalau app ke-kill paksa (low memory,
    // force-stop) di tengah nonton, progres sejak save terakhir hilang dan "Lanjutkan
    // Nonton" jadi mundur jauh dari posisi terakhir yang sebenarnya.
    LaunchedEffect(player) {
        while (true) {
            delay(10_000L)
            episodes.getOrNull(currentIndex)?.let {
                repository.saveWatchedPosition(it.path, player.currentPosition, it.duration, titleFolderPath)
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Sleep timer
    LaunchedEffect(sleepMinutesLeft) {
        if (sleepMinutesLeft > 0) {
            delay(60_000L)
            if (sleepMinutesLeft <= 1) { player.pause(); sleepMinutesLeft = 0 }
            else sleepMinutesLeft--
        }
    }

    // Gesture hint hide
    LaunchedEffect(gestureLabel) {
        if (gestureLabel.isNotEmpty()) {
            showGestureHint = true
            delay(1200)
            showGestureHint = false
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            player.seekTo(maxOf(0L, player.currentPosition - 10_000L))
                            gestureLabel = "« 10 detik"
                        } else {
                            val target = player.currentPosition + 10_000L
                            player.seekTo(if (duration > 0) minOf(target, duration - 500L) else target)
                            gestureLabel = "10 detik »"
                        }
                        showControls = true
                    }
                )
            }
            .pointerInput(Unit) {
                var dragStartX = 0f
                var dragStartVolume = 0
                var dragStartBrightness = 0
                var isDraggingVertical = false

                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        dragStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        dragStartBrightness = try {
                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                        } catch (e: Exception) { 128 }
                        isDraggingVertical = false
                    },
                    onDrag = { _, dragAmount ->
                        if (!isDraggingVertical && abs(dragAmount.y) > abs(dragAmount.x) * 1.5f)
                            isDraggingVertical = true
                        if (!isDraggingVertical) return@detectDragGestures

                        if (dragStartX < size.width / 2) {
                            val delta = (-dragAmount.y / size.height * 255).toInt()
                            val newBright = (currentBrightness + delta).coerceIn(1, 255)
                            currentBrightness = newBright
                            try {
                                activity?.window?.let { w ->
                                    val p = w.attributes
                                    p.screenBrightness = newBright / 255f
                                    w.attributes = p
                                }
                            } catch (e: Exception) { }
                            gestureLabel = "☀ ${(newBright / 255f * 100).toInt()}%"
                        } else {
                            val delta = (-dragAmount.y / size.height * maxVolume).toInt()
                            val newVol = (currentVolume + delta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            currentVolume = newVol
                            gestureLabel = "♪ ${(newVol.toFloat() / maxVolume * 100).toInt()}%"
                        }
                        showGestureHint = true
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply { this.player = player; useController = false }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture feedback
        AnimatedVisibility(
            visible = showGestureHint,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(gestureLabel, color = FxWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {

                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        episodes.getOrNull(currentIndex)?.let {
                            repository.saveWatchedPosition(it.path, player.currentPosition, it.duration, titleFolderPath)
                        }
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, null, tint = FxWhite)
                    }

                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            episodes.getOrNull(currentIndex)?.title ?: "",
                            color = FxWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "EP ${currentIndex + 1} / ${episodes.size}",
                            color = FxLightGray, fontSize = 11.sp
                        )
                    }

                    // Sleep timer
                    Box {
                        IconButton(onClick = { showSleepMenu = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.NightlightRound, null,
                                    tint = if (sleepMinutesLeft > 0) FxRed else FxWhite,
                                    modifier = Modifier.size(20.dp))
                                if (sleepMinutesLeft > 0)
                                    Text("${sleepMinutesLeft}m", color = FxRed, fontSize = 9.sp)
                            }
                        }
                        DropdownMenu(expanded = showSleepMenu,
                            onDismissRequest = { showSleepMenu = false },
                            modifier = Modifier.background(FxDark)) {
                            listOf(0, 5, 10, 15, 30, 60).forEach { min ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (min == 0) "Matikan Timer" else "$min menit",
                                            color = if (min == sleepMinutesLeft) FxRed else FxWhite
                                        )
                                    },
                                    onClick = { sleepMinutesLeft = min; showSleepMenu = false }
                                )
                            }
                        }
                    }

                    // Repeat
                    IconButton(onClick = { isRepeat = !isRepeat }) {
                        Icon(Icons.Default.Repeat, null,
                            tint = if (isRepeat) FxRed else FxWhite)
                    }

                    // Speed
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Speed, null, tint = FxWhite,
                                    modifier = Modifier.size(20.dp))
                                Text(
                                    if (playbackSpeed % 1 == 0f) "${playbackSpeed.toInt()}x"
                                    else "${playbackSpeed}x",
                                    color = FxWhite, fontSize = 9.sp
                                )
                            }
                        }
                        DropdownMenu(expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            modifier = Modifier.background(FxDark)) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (spd % 1 == 0f) "${spd.toInt()}x" else "${spd}x",
                                            color = if (spd == playbackSpeed) FxRed else FxWhite,
                                            fontWeight = if (spd == playbackSpeed) FontWeight.Bold
                                            else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        playbackSpeed = spd
                                        player.setPlaybackSpeed(spd)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Subtitle & Audio track (P3)
                    IconButton(onClick = { showTracksDialog = true }) {
                        Icon(Icons.Default.Subtitles, "Subtitle & Audio",
                            tint = if (selectedSubtitle != null) FxRed else FxWhite)
                    }
                }

                // CENTER CONTROLS
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ← Episode sebelumnya (murni pindah episode, tanpa logika posisi)
                    IconButton(
                        onClick = { if (currentIndex > 0) goToEpisode(currentIndex - 1) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, null,
                            tint = if (currentIndex > 0) FxWhite else FxGray,
                            modifier = Modifier.size(38.dp))
                    }

                    // Mundur 10 detik
                    IconButton(
                        onClick = { player.seekTo(maxOf(0L, player.currentPosition - 10_000L)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Replay10, null, tint = FxWhite,
                            modifier = Modifier.size(34.dp))
                    }

                    // Play / Pause
                    IconButton(
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier
                            .size(70.dp)
                            .background(FxWhite.copy(alpha = 0.18f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = FxWhite, modifier = Modifier.size(44.dp)
                        )
                    }

                    // Maju 10 detik
                    IconButton(
                        onClick = {
                            val target = player.currentPosition + 10_000L
                            player.seekTo(if (duration > 0) minOf(target, duration - 500L) else target)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Forward10, null, tint = FxWhite,
                            modifier = Modifier.size(34.dp))
                    }

                    // → Episode berikutnya (murni pindah episode)
                    IconButton(
                        onClick = { if (currentIndex < episodes.size - 1) goToEpisode(currentIndex + 1) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, null,
                            tint = if (currentIndex < episodes.size - 1) FxWhite else FxGray,
                            modifier = Modifier.size(38.dp))
                    }
                }

                // BOTTOM SEEKBAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val sliderVal = remember(seekingTo, currentPosition, duration) {
                        val pos = seekingTo ?: currentPosition
                        if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    }

                    Slider(
                        value = sliderVal,
                        onValueChange = { seekingTo = (it * duration).toLong() },
                        onValueChangeFinished = {
                            seekingTo?.let { player.seekTo(it) }
                            seekingTo = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = FxRed,
                            activeTrackColor = FxRed,
                            inactiveTrackColor = FxWhite.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(seekingTo ?: currentPosition),
                            color = FxWhite, fontSize = 12.sp)
                        Text(formatTime(duration), color = FxGray, fontSize = 12.sp)
                    }
                }

                // Side bars
                VolumeBar(value = currentVolume.toFloat() / maxVolume,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
                BrightnessBar(value = currentBrightness / 255f,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
            }
        }
    }

    // ── Dialog Subtitle & Audio (P3) ─────────────────────────────────────────
    if (showTracksDialog) {
        TracksDialog(
            availableSubtitles = availableSubtitles,
            selectedSubtitle = selectedSubtitle,
            subtitleDelayMs = subtitleDelayMs,
            audioTracks = audioTracks,
            onSelectSubtitle = { file ->
                selectedSubtitle = file
                subtitleDelayMs = file?.let { repository.getSubtitleDelayMs(it.path) } ?: 0L
                applySubtitle(file, subtitleDelayMs)
            },
            onDelayChange = { newDelay ->
                subtitleDelayMs = newDelay
                selectedSubtitle?.let {
                    repository.saveSubtitleDelayMs(it.path, newDelay)
                    applySubtitle(it, newDelay)
                }
            },
            onAddSubtitleManual = { showTracksDialog = false; showSubtitlePickerDialog = true },
            onSelectAudioTrack = { selectAudioTrack(it) },
            onDismiss = { showTracksDialog = false }
        )
    }

    if (showSubtitlePickerDialog) {
        SubtitleFilePickerDialog(
            onPicked = { picked ->
                showSubtitlePickerDialog = false
                episodes.getOrNull(currentIndex)?.let { ep ->
                    val attached = repository.attachSubtitle(ep.path, picked)
                    if (attached != null) {
                        availableSubtitles = repository.findSubtitlesForEpisode(ep.path)
                        selectedSubtitle = attached
                        subtitleDelayMs = 0L
                        applySubtitle(attached, 0L)
                    }
                }
            },
            onDismiss = { showSubtitlePickerDialog = false }
        )
    }
}

@Composable
fun VolumeBar(value: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.VolumeUp, null, tint = FxWhite.copy(0.4f), modifier = Modifier.size(13.dp))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(4.dp).height(70.dp).background(FxWhite.copy(0.18f), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .background(FxWhite.copy(0.7f), RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
fun BrightnessBar(value: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.BrightnessHigh, null, tint = FxWhite.copy(0.4f), modifier = Modifier.size(13.dp))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(4.dp).height(70.dp).background(FxWhite.copy(0.18f), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .background(Color(0xFFFFD700).copy(0.75f), RoundedCornerShape(2.dp)))
        }
    }
}

// ── Dialog Subtitle & Audio (P3) ────────────────────────────────────────────
@Composable
private fun TracksDialog(
    availableSubtitles: List<File>,
    selectedSubtitle: File?,
    subtitleDelayMs: Long,
    audioTracks: List<AudioTrackChoice>,
    onSelectSubtitle: (File?) -> Unit,
    onDelayChange: (Long) -> Unit,
    onAddSubtitleManual: () -> Unit,
    onSelectAudioTrack: (AudioTrackChoice) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                .background(FxDark, RoundedCornerShape(12.dp)).padding(16.dp)
        ) {
            Text("Subtitle & Audio", color = FxWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Text("Subtitle", color = FxGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            TrackRow(label = "Tidak ada", selected = selectedSubtitle == null) { onSelectSubtitle(null) }
            availableSubtitles.forEach { f ->
                TrackRow(label = f.name, selected = selectedSubtitle?.path == f.path) { onSelectSubtitle(f) }
            }
            TrackRow(label = "+ Tambah Subtitle Manual...", selected = false, accent = true) {
                onAddSubtitleManual()
            }

            if (selectedSubtitle != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sinkronisasi (geser kalau subtitle nggak pas)", color = FxGray, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { onDelayChange(subtitleDelayMs - 500L) },
                        border = BorderStroke(1.dp, FxGray)) {
                        Text("-0.5s", color = FxWhite, fontSize = 12.sp)
                    }
                    Text("${subtitleDelayMs}ms", color = FxWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { onDelayChange(subtitleDelayMs + 500L) },
                        border = BorderStroke(1.dp, FxGray)) {
                        Text("+0.5s", color = FxWhite, fontSize = 12.sp)
                    }
                }
                if (subtitleDelayMs != 0L) {
                    TextButton(onClick = { onDelayChange(0L) }) {
                        Text("Reset ke 0", color = FxRed, fontSize = 12.sp)
                    }
                }
            }

            if (audioTracks.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Audio", color = FxGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                audioTracks.forEach { track ->
                    TrackRow(label = track.label.uppercase(), selected = track.isSelected) {
                        onSelectAudioTrack(track)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, FxGray)) {
                Text("Tutup", color = FxGray)
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            null, tint = if (selected) FxRed else FxGray, modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = if (accent) FxRed else FxWhite, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// File picker sederhana khusus subtitle — dipakai buat kasus nama file subtitle tidak
// cocok dengan video-nya, jadi tidak kedetect otomatis oleh findSubtitlesForEpisode().
// Mulai dari root penyimpanan internal karena file subtitle hasil download terpisah
// biasanya ada di folder lain (Download dkk), bukan di folder video itu sendiri.
@Composable
private fun SubtitleFilePickerDialog(onPicked: (File) -> Unit, onDismiss: () -> Unit) {
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val items = remember(currentDir) {
        currentDir.listFiles()?.filter { f ->
            f.isDirectory || f.extension.lowercase() in MediaRepository.SUBTITLE_EXTENSIONS
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                .background(FxDark, RoundedCornerShape(12.dp)).padding(16.dp)
        ) {
            Text("Pilih File Subtitle", color = FxWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(currentDir.absolutePath, color = FxGray, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))

            currentDir.parentFile?.let { parent ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { currentDir = parent }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowUpward, null, tint = FxGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("..", color = FxGray, fontSize = 13.sp)
                }
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(items) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { if (item.isDirectory) currentDir = item else onPicked(item) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (item.isDirectory) Icons.Default.Folder else Icons.Default.Subtitles,
                            null, tint = if (item.isDirectory) FxGray else FxRed, modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item.name, color = FxWhite, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, FxGray)) {
                Text("Batal", color = FxGray)
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val h = ms / 3_600_000L
    val m = (ms % 3_600_000L) / 60_000L
    val s = (ms % 60_000L) / 1_000L
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
