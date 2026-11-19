package com.flixbox.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.flixbox.app.data.EpisodeModel
import com.flixbox.app.data.MediaModel
import com.flixbox.app.data.MediaRepository
import com.flixbox.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    repository: MediaRepository,
    allMedia: List<MediaModel>,
    onMediaClick: (MediaModel) -> Unit,
    onEpisodeClick: (MediaModel, EpisodeModel) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val results = remember(query, allMedia) {
        if (query.isBlank()) emptyList()
        else allMedia.filter { it.title.contains(query, ignoreCase = true) }
    }

    // P4: pencarian episode — sengaja di-debounce (tunggu user berhenti ngetik dulu)
    // karena ini scan nama file semua episode di semua judul, bukan filter in-memory
    // instan kayak pencarian judul. Biar tidak keulang-ulang tiap keystroke.
    var episodeResults by remember { mutableStateOf<List<MediaRepository.EpisodeSearchResult>>(emptyList()) }
    var isSearchingEpisodes by remember { mutableStateOf(false) }
    LaunchedEffect(query, allMedia) {
        if (query.isBlank()) {
            episodeResults = emptyList()
            isSearchingEpisodes = false
        } else {
            isSearchingEpisodes = true
            delay(400)
            episodeResults = repository.searchEpisodes(allMedia, query)
            isSearchingEpisodes = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(FxBlack).statusBarsPadding()) {

        // Search bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = FxWhite)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari judul...", color = FxGray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FxRed,
                    unfocusedBorderColor = FxCard,
                    focusedTextColor = FxWhite,
                    unfocusedTextColor = FxWhite,
                    cursorColor = FxRed,
                    focusedContainerColor = FxDark,
                    unfocusedContainerColor = FxDark
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, null, tint = FxGray)
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = FxCard)

        if (query.isBlank()) {
            // Browse all by category
            BrowseAll(allMedia = allMedia, repository = repository, onMediaClick = onMediaClick)
        } else if (results.isEmpty() && episodeResults.isEmpty() && !isSearchingEpisodes) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, null, tint = FxGray, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tidak ditemukan \"$query\"", color = FxGray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)) {
                if (results.isNotEmpty()) {
                    item {
                        Text("Judul (${results.size})", color = FxGray, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    items(results) { media ->
                        SearchResultItem(media, repository, onClick = { onMediaClick(media) })
                    }
                }
                if (isSearchingEpisodes || episodeResults.isNotEmpty()) {
                    item {
                        Text(
                            if (isSearchingEpisodes) "Mencari episode..." else "Episode (${episodeResults.size})",
                            color = FxGray, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(episodeResults) { r ->
                        EpisodeSearchResultItem(r, repository, onClick = { onEpisodeClick(r.media, r.episode) })
                    }
                }
            }
        }
    }
}

@Composable
fun BrowseAll(
    allMedia: List<MediaModel>,
    repository: MediaRepository,
    onMediaClick: (MediaModel) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text("Semua Kategori", color = FxWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        // Group by category
        val grouped = allMedia.groupBy { it.category }
        grouped.forEach { (cat, items) ->
            item {
                BrowseCategoryCard(
                    label = cat.label,
                    count = items.size,
                    sample = items.take(3),
                    repository = repository
                )
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun BrowseCategoryCard(
    label: String,
    count: Int,
    sample: List<MediaModel>,
    repository: MediaRepository
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp)).background(FxCard).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini thumbnails
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sample.take(3).forEach { media ->
                var bitmap by remember(media.firstEpisodePath) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(media.firstEpisodePath) {
                    if (media.posterPath.isEmpty()) bitmap = repository.getThumbnail(media.firstEpisodePath)
                }
                Box(modifier = Modifier.width(52.dp).height(74.dp).clip(RoundedCornerShape(4.dp)).background(FxDark)) {
                    when {
                        media.posterPath.isNotEmpty() ->
                            AsyncImage(media.posterPath, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        bitmap != null ->
                            Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, color = FxWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("$count judul", color = FxGray, fontSize = 12.sp)
        }
    }
}

// P4 — item hasil pencarian episode: thumbnail episode + judul induk + nama episode
@Composable
fun EpisodeSearchResultItem(
    result: MediaRepository.EpisodeSearchResult,
    repository: MediaRepository,
    onClick: () -> Unit
) {
    val episode = result.episode
    var bitmap by remember(episode.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(episode.path) { bitmap = repository.getThumbnail(episode.path) }

    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(96.dp).height(54.dp)
            .clip(RoundedCornerShape(6.dp)).background(FxCard)) {
            if (bitmap != null) {
                Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(episode.title, color = FxWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(result.media.title, color = FxGray, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayCircle, null, tint = FxRed, modifier = Modifier.size(22.dp))
    }
    HorizontalDivider(color = FxCard, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun SearchResultItem(media: MediaModel, repository: MediaRepository, onClick: () -> Unit) {
    var bitmap by remember(media.firstEpisodePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(media.firstEpisodePath) {
        if (media.posterPath.isEmpty()) bitmap = repository.getThumbnail(media.firstEpisodePath)
    }

    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(80.dp).height(112.dp)
            .clip(RoundedCornerShape(6.dp)).background(FxCard)) {
            when {
                media.posterPath.isNotEmpty() ->
                    AsyncImage(media.posterPath, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                bitmap != null ->
                    Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> ShimmerBox(modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(media.title, color = FxWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(media.category.label, color = FxRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("${media.episodeCount} Episode", color = FxGray, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = FxGray)
    }
    HorizontalDivider(color = FxCard, modifier = Modifier.padding(horizontal = 16.dp))
}
