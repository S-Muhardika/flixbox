package com.flixbox.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.flixbox.app.data.MediaCategory
import com.flixbox.app.data.MediaModel
import com.flixbox.app.data.MediaRepository
import com.flixbox.app.data.SortOption
import com.flixbox.app.data.WatchStatus
import com.flixbox.app.ui.theme.*
import kotlinx.coroutines.delay

// ── Shimmer helper ─────────────────────────────────────────────────────────────
@Composable
fun ShimmerBox(modifier: Modifier) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val offset by shimmer.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer_offset"
    )
    Box(modifier = modifier.background(
        brush = Brush.linearGradient(
            colors = listOf(FxCard, Color(0xFF333333), FxCard),
            start = Offset(offset * 400f, 0f),
            end = Offset((offset + 1) * 400f, 0f)
        )
    ))
}

// ── Home ───────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    repository: MediaRepository,
    mediaList: List<MediaModel>,
    isLoading: Boolean,
    onMediaClick: (MediaModel) -> Unit,
    onContinueWatchingClick: (MediaModel) -> Unit,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(MediaCategory.ALL) }
    var heroBannerIndex by remember { mutableStateOf(0) }
    // P4: filter status tontonan & sorting, digabung satu dialog biar navbar tidak penuh
    var statusFilter by remember { mutableStateOf<WatchStatus?>(null) } // null = semua status
    var sortOption by remember { mutableStateOf(SortOption.TITLE_ASC) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // mediaList & isLoading sekarang dikirim dari FlixBoxApp (satu sumber data), bukan
    // di-fetch ulang di sini. Sebelumnya HomeScreen punya LaunchedEffect sendiri yang
    // manggil repository.getAllMedia() lagi — artinya seluruh koleksi di-scan filesystem DUA
    // KALI setiap kali Home tampil (sekali di FlixBoxApp untuk SearchScreen, sekali lagi di
    // sini), padahal hasilnya sama persis. Ini salah satu penyebab app terasa berat.

    // Auto-rotate hero banner tiap 6 detik
    LaunchedEffect(mediaList) {
        if (mediaList.size > 1) {
            while (true) {
                delay(6000)
                heroBannerIndex = (heroBannerIndex + 1) % mediaList.size
            }
        }
    }

    val filteredList = remember(mediaList, selectedCategory, statusFilter, sortOption) {
        var list = if (selectedCategory == MediaCategory.ALL) mediaList
            else mediaList.filter { it.category == selectedCategory }
        statusFilter?.let { status -> list = list.filter { repository.getWatchStatus(it) == status } }
        list = when (sortOption) {
            SortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.dateAdded }
        }
        list
    }

    val recentMedia = remember(mediaList) {
        repository.getRecentlyWatched()
            .mapNotNull { (path, _) -> mediaList.firstOrNull { path.startsWith(it.folderPath) } }
            .distinctBy { it.folderPath }.take(15)
    }

    // P4: Favorite & Watchlist — section di Home, independen dari filter kategori di atas
    val favoriteMedia = remember(mediaList) { mediaList.filter { repository.isFavorite(it.folderPath) } }
    val watchlistMedia = remember(mediaList) { mediaList.filter { repository.isInWatchlist(it.folderPath) } }

    val featuredMedia = mediaList.getOrNull(heroBannerIndex)

    Box(modifier = Modifier.fillMaxSize().background(FxBlack)) {

        if (isLoading) {
            // Shimmer skeleton
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(480.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(2) {
                    ShimmerBox(modifier = Modifier.width(100.dp).height(16.dp)
                        .padding(start = 12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) {
                            ShimmerBox(modifier = Modifier.width(120.dp).height(180.dp)
                                .clip(RoundedCornerShape(6.dp)))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else if (mediaList.isEmpty()) {
            EmptyState(onAddClick = onAddClick)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // Hero banner
                item {
                    AnimatedContent(
                        targetState = featuredMedia,
                        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                        label = "hero"
                    ) { media ->
                        HeroBanner(
                            media = media,
                            repository = repository,
                            dotCount = mediaList.size,
                            dotIndex = heroBannerIndex,
                            onClick = { media?.let { onMediaClick(it) } }
                        )
                    }
                }

                // Category tabs
                item {
                    CategoryNav(
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it },
                        onFilterClick = { showFilterDialog = true }
                    )
                }

                // Lanjutkan Nonton
                if (recentMedia.isNotEmpty()) {
                    item {
                        SectionRow(
                            title = "Lanjutkan Nonton",
                            items = recentMedia,
                            repository = repository,
                            cardType = CardType.WIDE,
                            onItemClick = onContinueWatchingClick
                        )
                    }
                }

                // Favorite & Watchlist (P4)
                if (favoriteMedia.isNotEmpty()) {
                    item {
                        SectionRow(
                            title = "Favorite",
                            items = favoriteMedia,
                            repository = repository,
                            cardType = CardType.PORTRAIT,
                            onItemClick = onMediaClick
                        )
                    }
                }
                if (watchlistMedia.isNotEmpty()) {
                    item {
                        SectionRow(
                            title = "Watchlist",
                            items = watchlistMedia,
                            repository = repository,
                            cardType = CardType.PORTRAIT,
                            onItemClick = onMediaClick
                        )
                    }
                }

                // Per kategori
                val cats = if (selectedCategory == MediaCategory.ALL)
                    listOf(MediaCategory.ANIME, MediaCategory.MOVIE,
                           MediaCategory.SERIES, MediaCategory.LIVE_ACTION)
                else listOf(selectedCategory)

                cats.forEach { cat ->
                    val items = filteredList.filter { it.category == cat }
                    if (items.isNotEmpty()) {
                        item {
                            SectionRow(
                                title = cat.label,
                                items = items,
                                repository = repository,
                                cardType = CardType.PORTRAIT,
                                onItemClick = onMediaClick
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // Top navbar overlay
        TopNavBar(
            onSearchClick = onSearchClick,
            onAddClick = onAddClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showFilterDialog) {
        FilterSortDialog(
            statusFilter = statusFilter,
            sortOption = sortOption,
            onApply = { newStatus, newSort ->
                statusFilter = newStatus
                sortOption = newSort
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

// ── Top Navbar ─────────────────────────────────────────────────────────────────
@Composable
fun TopNavBar(
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().height(80.dp).background(
            Brush.verticalGradient(listOf(FxBlack.copy(alpha = 0.9f), Color.Transparent))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FLIXBOX", color = FxRed, fontSize = 26.sp,
                fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, "Cari", tint = FxWhite)
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, "Tambah", tint = FxWhite)
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Pengaturan", tint = FxWhite)
                }
            }
        }
    }
}

// ── Hero Banner ────────────────────────────────────────────────────────────────
@Composable
fun HeroBanner(
    media: MediaModel?,
    repository: MediaRepository,
    dotCount: Int,
    dotIndex: Int,
    onClick: () -> Unit
) {
    var bitmap by remember(media?.firstEpisodePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(media?.firstEpisodePath) {
        bitmap = null
        media?.let {
            if (it.posterPath.isEmpty()) bitmap = repository.getThumbnail(it.firstEpisodePath, 60_000_000L)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(500.dp).clickable { onClick() }) {
        when {
            media?.posterPath?.isNotEmpty() == true ->
                AsyncImage(model = media.posterPath, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            bitmap != null ->
                Image(bitmap!!.asImageBitmap(), null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Box(modifier = Modifier.fillMaxSize().background(FxDark))
        }

        // Gradient overlay bottom
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.82f to FxBlack.copy(alpha = 0.7f),
                    1.0f to FxBlack
                )
            )
        ))

        // Content bottom
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(media?.title ?: "", color = FxWhite, fontSize = 30.sp,
                fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(media?.category?.label ?: "", color = FxLightGray, fontSize = 13.sp)
                Text("•", color = FxGray, fontSize = 13.sp)
                Text("${media?.episodeCount ?: 0} Episode", color = FxLightGray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Dot indicators
            if (dotCount > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 14.dp)) {
                    repeat(minOf(dotCount, 8)) { i ->
                        Box(modifier = Modifier
                            .size(if (i == dotIndex) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == dotIndex) FxRed else FxGray.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = FxWhite),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(40.dp).widthIn(min = 110.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Putar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onClick,
                    border = BorderStroke(1.dp, FxWhite),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(40.dp).widthIn(min = 110.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = FxWhite, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Info", color = FxWhite)
                }
            }
        }

        // Top gradient
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(
            Brush.verticalGradient(listOf(FxBlack.copy(alpha = 0.8f), Color.Transparent))
        ))
    }
}

// ── Category Nav ───────────────────────────────────────────────────────────────
@Composable
fun CategoryNav(selected: MediaCategory, onSelect: (MediaCategory) -> Unit, onFilterClick: () -> Unit) {
    val cats = listOf(MediaCategory.ALL, MediaCategory.ANIME,
        MediaCategory.MOVIE, MediaCategory.SERIES, MediaCategory.LIVE_ACTION)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cats.forEach { cat ->
                val isSelected = cat == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) FxRed else FxCard)
                        .clickable { onSelect(cat) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(cat.label, color = if (isSelected) FxWhite else FxLightGray,
                        fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        // P4: Filter (status tontonan) & Urutkan, digabung satu dialog
        IconButton(onClick = onFilterClick, modifier = Modifier.padding(end = 8.dp)) {
            Icon(Icons.Default.FilterList, "Filter & Urutkan", tint = FxWhite)
        }
    }
}

// P4 — Filter (status tontonan) & Urutkan koleksi, digabung satu dialog
@Composable
fun FilterSortDialog(
    statusFilter: WatchStatus?,
    sortOption: SortOption,
    onApply: (WatchStatus?, SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    var status by remember { mutableStateOf(statusFilter) }
    var sort by remember { mutableStateOf(sortOption) }

    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()
            .background(FxDark, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Text("Filter & Urutkan", color = FxWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Status Tontonan", color = FxGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            FilterOptionRow("Semua", status == null) { status = null }
            FilterOptionRow("Belum Ditonton", status == WatchStatus.BELUM_DITONTON) { status = WatchStatus.BELUM_DITONTON }
            FilterOptionRow("Sedang Ditonton", status == WatchStatus.SEDANG_DITONTON) { status = WatchStatus.SEDANG_DITONTON }
            FilterOptionRow("Selesai", status == WatchStatus.SELESAI) { status = WatchStatus.SELESAI }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Urutkan", color = FxGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            SortOption.values().forEach { opt ->
                FilterOptionRow(opt.label, sort == opt) { sort = opt }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, FxGray)) { Text("Batal", color = FxGray) }
                Button(onClick = { onApply(status, sort) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FxRed)) {
                    Text("Terapkan", color = FxWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilterOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            null, tint = if (selected) FxRed else FxGray, modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = FxWhite, fontSize = 13.sp)
    }
}

// ── Section Row ────────────────────────────────────────────────────────────────
enum class CardType { PORTRAIT, WIDE }

@Composable
fun SectionRow(
    title: String,
    items: List<MediaModel>,
    repository: MediaRepository,
    cardType: CardType,
    onItemClick: (MediaModel) -> Unit
) {
    Column {
        Text(title, color = FxWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp, top = 6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { media ->
                if (cardType == CardType.PORTRAIT) {
                    PortraitCard(media, repository) { onItemClick(media) }
                } else {
                    WideCard(media, repository) { onItemClick(media) }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ── Portrait Card (2:3) ────────────────────────────────────────────────────────
@Composable
fun PortraitCard(media: MediaModel, repository: MediaRepository, onClick: () -> Unit) {
    var bitmap by remember(media.firstEpisodePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(media.firstEpisodePath) {
        if (media.posterPath.isEmpty()) bitmap = repository.getThumbnail(media.firstEpisodePath)
    }

    Box(modifier = Modifier.width(110.dp).height(165.dp)
        .clip(RoundedCornerShape(6.dp)).background(FxCard).clickable { onClick() }
    ) {
        when {
            media.posterPath.isNotEmpty() ->
                AsyncImage(model = media.posterPath, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            bitmap != null ->
                Image(bitmap!!.asImageBitmap(), null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> ShimmerBox(modifier = Modifier.fillMaxSize())
        }
        // Bottom gradient + title
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 80f)
        ))
        Text(media.title, color = FxWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

// ── Wide Card (16:9) ───────────────────────────────────────────────────────────
@Composable
fun WideCard(media: MediaModel, repository: MediaRepository, onClick: () -> Unit) {
    var bitmap by remember(media.firstEpisodePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(media.firstEpisodePath) {
        if (media.posterPath.isEmpty()) bitmap = repository.getThumbnail(media.firstEpisodePath)
    }

    Box(modifier = Modifier.width(175.dp).height(98.dp)
        .clip(RoundedCornerShape(6.dp)).background(FxCard).clickable { onClick() }
    ) {
        when {
            media.posterPath.isNotEmpty() ->
                AsyncImage(model = media.posterPath, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            bitmap != null ->
                Image(bitmap!!.asImageBitmap(), null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> ShimmerBox(modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))
        ))
        Icon(Icons.Default.PlayCircle, null, tint = FxWhite.copy(alpha = 0.75f),
            modifier = Modifier.size(30.dp).align(Alignment.Center))
        Text(media.title, color = FxWhite, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

// ── Empty State ────────────────────────────────────────────────────────────────
@Composable
fun EmptyState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.VideoLibrary, null, tint = FxGray, modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Belum ada koleksi", color = FxWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Tambah judul baru atau import dari storage",
            color = FxGray, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = FxRed),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.Add, null, tint = FxWhite)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tambah Sekarang", color = FxWhite, fontWeight = FontWeight.Bold)
        }
    }
}
