package com.flixbox.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.flixbox.app.data.EpisodeModel
import com.flixbox.app.data.MediaCategory
import com.flixbox.app.data.MediaModel
import com.flixbox.app.data.MediaRepository
import com.flixbox.app.data.SeasonModel
import com.flixbox.app.ui.theme.*
import java.util.concurrent.TimeUnit

@Composable
fun DetailScreen(
    media: MediaModel,
    repository: MediaRepository,
    onBack: () -> Unit,
    onPlayEpisode: (EpisodeModel, List<EpisodeModel>) -> Unit,
    onDelete: (MediaModel) -> Unit
) {
    var seasons by remember { mutableStateOf(listOf<SeasonModel>()) }
    var flatEpisodes by remember { mutableStateOf(listOf<EpisodeModel>()) }
    var bannerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedEpisode by remember { mutableStateOf<EpisodeModel?>(null) }
    // P4: Favorite & Watchlist — dibaca sekali per folderPath, diubah langsung tiap tap
    var isFavorite by remember(media.folderPath) { mutableStateOf(repository.isFavorite(media.folderPath)) }
    var isInWatchlist by remember(media.folderPath) { mutableStateOf(repository.isInWatchlist(media.folderPath)) }

    LaunchedEffect(media.folderPath) {
        if (media.hasSeasons) {
            val initial = repository.getSeasons(media.folderPath)
            seasons = initial

            // Preload semua thumbnail season terpilih (index 0) di background
            initial.firstOrNull()?.episodes?.map { it.path }?.let {
                kotlinx.coroutines.launch { repository.preloadThumbnails(it) }
            }

            // Update durasi incremental
            repository.loadSeasonDurationsFlow(initial).collect { seasons = it }
        } else {
            val initial = repository.getEpisodes(media.folderPath)
            flatEpisodes = initial

            // Preload semua thumbnail di background
            kotlinx.coroutines.launch {
                repository.preloadThumbnails(initial.map { it.path })
            }

            // Update durasi incremental
            repository.loadDurationsFlow(initial).collect { flatEpisodes = it }
        }

        if (media.posterPath.isEmpty() && media.firstEpisodePath.isNotEmpty()) {
            bannerBitmap = repository.getThumbnail(media.firstEpisodePath, 60_000_000L)
        }
    }

    // Preload thumbnail saat ganti season
    LaunchedEffect(selectedSeasonIndex) {
        seasons.getOrNull(selectedSeasonIndex)?.episodes?.map { it.path }?.let {
            repository.preloadThumbnails(it)
        }
    }

    val displayedEpisodes = remember(media.hasSeasons, seasons, flatEpisodes, selectedSeasonIndex) {
        if (media.hasSeasons) seasons.getOrNull(selectedSeasonIndex)?.episodes ?: emptyList()
        else flatEpisodes
    }

    val allEpisodes = remember(media.hasSeasons, seasons, flatEpisodes) {
        if (media.hasSeasons) seasons.flatMap { it.episodes } else flatEpisodes
    }

    if (showEditInfoDialog) {
        EditInfoDialog(
            currentName = media.title,
            currentCategory = media.category,
            onDismiss = { showEditInfoDialog = false }
        ) { newName, newCategory ->
            repository.editMediaInfo(media.folderPath, newName, newCategory)
            showEditInfoDialog = false
            onBack()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = FxDark,
            title = { Text("Hapus \"${media.title}\"?", color = FxWhite) },
            text = { Text("Semua episode akan dihapus permanen.", color = FxGray) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete(media) }) {
                    Text("Hapus", color = FxRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal", color = FxGray) }
            }
        )
    }

    selectedEpisode?.let { ep ->
        EpisodeMenuDialog(
            episode = ep,
            onDismiss = { selectedEpisode = null },
            onDelete = { repository.deleteFile(ep.path); selectedEpisode = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(FxBlack)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // ── Banner ────────────────────────────────────────────────────────
            item {
                Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    when {
                        media.posterPath.isNotEmpty() ->
                            AsyncImage(model = media.posterPath, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        bannerBitmap != null ->
                            Image(bannerBitmap!!.asImageBitmap(), null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else -> Box(modifier = Modifier.fillMaxSize().background(FxDark))
                    }
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(colorStops = arrayOf(
                            0f to Color.Transparent, 0.5f to Color.Transparent,
                            0.85f to FxBlack.copy(0.8f), 1f to FxBlack
                        ))
                    ))
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(
                        Brush.verticalGradient(listOf(FxBlack.copy(0.8f), Color.Transparent))
                    ))

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(media.title, color = FxWhite, fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(media.category.label)
                            Text("•", color = FxGray)
                            if (media.hasSeasons) {
                                Text("${seasons.size} Season", color = FxLightGray, fontSize = 13.sp)
                                Text("•", color = FxGray)
                            }
                            Text("${media.episodeCount} Episode", color = FxLightGray, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                displayedEpisodes.firstOrNull()?.let { onPlayEpisode(it, allEpisodes) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FxWhite),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black,
                                modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Putar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            ActionBtn(
                                icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                label = "Favorite",
                                tint = if (isFavorite) FxRed else FxWhite
                            ) {
                                isFavorite = !isFavorite
                                repository.setFavorite(media.folderPath, isFavorite)
                            }
                            ActionBtn(
                                icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                label = "Watchlist",
                                tint = if (isInWatchlist) FxRed else FxWhite
                            ) {
                                isInWatchlist = !isInWatchlist
                                repository.setWatchlist(media.folderPath, isInWatchlist)
                            }
                            ActionBtn(Icons.Default.Edit, "Edit Info") { showEditInfoDialog = true }
                            ActionBtn(Icons.Default.Delete, "Hapus") { showDeleteDialog = true }
                        }
                    }
                }
            }

            // ── Season tabs ───────────────────────────────────────────────────
            if (media.hasSeasons && seasons.isNotEmpty()) {
                item {
                    HorizontalDivider(color = FxCard)
                    SeasonTabs(seasons = seasons, selectedIndex = selectedSeasonIndex,
                        onSelect = { selectedSeasonIndex = it })
                }
            }

            // ── Episode header ────────────────────────────────────────────────
            item {
                HorizontalDivider(color = FxCard)
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (media.hasSeasons && seasons.isNotEmpty())
                            seasons.getOrNull(selectedSeasonIndex)?.name ?: "Episodes"
                        else "Episodes",
                        color = FxWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text("${displayedEpisodes.size} episode", color = FxGray, fontSize = 12.sp)
                }
            }

            // ── Episodes — key by path agar tidak recompose seluruh list ──────
            items(displayedEpisodes, key = { it.path }) { ep ->
                EpisodeItem(
                    episode = ep,
                    repository = repository,
                    watchedPosition = repository.getWatchedPosition(ep.path),
                    isWatched = repository.isWatchedComplete(ep.path),
                    onClick = { onPlayEpisode(ep, allEpisodes) },
                    onMoreClick = { selectedEpisode = ep }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        IconButton(onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(4.dp).align(Alignment.TopStart)) {
            Icon(Icons.Default.ArrowBack, null, tint = FxWhite, modifier = Modifier.size(28.dp))
        }
    }
}

// ── Season Tabs ───────────────────────────────────────────────────────────────
@Composable
fun SeasonTabs(seasons: List<SeasonModel>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = FxBlack,
        contentColor = FxWhite,
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.BottomStart)
                        .offset(x = tabPositions[selectedIndex].left)
                        .width(tabPositions[selectedIndex].width),
                    color = FxRed
                )
            }
        },
        divider = {}
    ) {
        seasons.forEachIndexed { i, season ->
            Tab(selected = i == selectedIndex, onClick = { onSelect(i) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Text(season.name,
                        color = if (i == selectedIndex) FxWhite else FxGray, fontSize = 14.sp,
                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal)
                    Text("${season.episodes.size} ep",
                        color = if (i == selectedIndex) FxRed else FxGray, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── EpisodeItem — ambil thumbnail dari cache, tidak trigger load baru ─────────
@Composable
fun EpisodeItem(
    episode: EpisodeModel,
    repository: MediaRepository,
    watchedPosition: Long,
    isWatched: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    // produceState: recompose hanya kalau thumbnail berubah di cache
    val thumbnail by produceState<Bitmap?>(initialValue = repository.getCachedThumbnail(episode.path),
        key1 = episode.path) {
        // Kalau belum ada di cache, tunggu sebentar lalu cek ulang
        // (preloadThumbnails sudah jalan di background)
        if (value == null) {
            value = repository.getThumbnail(episode.path)
        }
    }

    val progress = if (episode.duration > 0)
        (watchedPosition.toFloat() / episode.duration).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(modifier = Modifier.width(130.dp).height(73.dp)
            .clip(RoundedCornerShape(6.dp)).background(FxCard)) {
            if (thumbnail != null) {
                Image(thumbnail!!.asImageBitmap(), null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }
            // Progress bar
            if (progress > 0.01f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                    color = FxRed, trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
            // Badge "sudah ditonton" (P2) — pojok kiri-atas biar tidak tabrakan sama durasi
            if (isWatched) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .size(18.dp).clip(RoundedCornerShape(50)).background(FxRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, "Sudah ditonton", tint = FxWhite,
                        modifier = Modifier.size(12.dp))
                }
            }
            // Duration badge
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (episode.duration > 0L) {
                    Text(formatDuration(episode.duration), color = FxWhite, fontSize = 10.sp)
                } else {
                    Box(modifier = Modifier.width(28.dp).height(8.dp).clip(RoundedCornerShape(2.dp))) {
                        ShimmerBox(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("EP ${episode.episodeNumber}", color = FxRed, fontSize = 11.sp,
                fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(episode.title, color = FxWhite, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(formatFileSize(episode.size), color = FxGray, fontSize = 11.sp)
        }

        IconButton(onClick = onMoreClick) {
            Icon(Icons.Default.MoreVert, null, tint = FxGray, modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(color = FxCard.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp))
}

// ── Shared composables ────────────────────────────────────────────────────────
@Composable
fun CategoryBadge(label: String) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
        .background(FxRed.copy(alpha = 0.2f))
        .border(1.dp, FxRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = FxRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = FxWhite,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = FxGray, fontSize = 11.sp)
    }
}

// P4 — edit metadata: judul & kategori sekaligus (gabungan dari fitur "Rename" lama)
@Composable
fun EditInfoDialog(
    currentName: String,
    currentCategory: MediaCategory,
    onDismiss: () -> Unit,
    onConfirm: (String, MediaCategory) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var category by remember { mutableStateOf(currentCategory) }
    val cats = listOf(MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.SERIES, MediaCategory.LIVE_ACTION)

    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()
            .background(FxDark, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Text("Edit Info", color = FxWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Judul", color = FxGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FxRed, unfocusedBorderColor = FxGray,
                    focusedTextColor = FxWhite, unfocusedTextColor = FxWhite, cursorColor = FxRed
                ), modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))
            Text("Kategori", color = FxGray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.take(2).forEach { cat ->
                    val sel = category == cat
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (sel) FxRed else FxCard)
                        .clickable { category = cat }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(cat.label, color = if (sel) FxWhite else FxGray, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.drop(2).forEach { cat ->
                    val sel = category == cat
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (sel) FxRed else FxCard)
                        .clickable { category = cat }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(cat.label, color = if (sel) FxWhite else FxGray, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, FxGray)) { Text("Batal", color = FxGray) }
                Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), category) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FxRed)) {
                    Text("Simpan", color = FxWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EpisodeMenuDialog(episode: EpisodeModel, onDismiss: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()
            .background(FxDark, RoundedCornerShape(12.dp)).padding(8.dp)) {
            Text(episode.title, color = FxGray, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            HorizontalDivider(color = FxCard)
            ListItem(headlineContent = { Text("Hapus Episode", color = FxRed) },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = FxRed) },
                modifier = Modifier.clickable { onDelete() },
                colors = ListItemDefaults.colors(containerColor = FxDark))
            ListItem(headlineContent = { Text("Batal", color = FxWhite) },
                leadingContent = { Icon(Icons.Default.Close, null, tint = FxWhite) },
                modifier = Modifier.clickable { onDismiss() },
                colors = ListItemDefaults.colors(containerColor = FxDark))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%02d:%02d".format(m, s)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.1f KB".format(bytes / 1024.0)
}
