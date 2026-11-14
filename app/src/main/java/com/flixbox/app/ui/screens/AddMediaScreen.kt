package com.flixbox.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.flixbox.app.data.BrowserItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.flixbox.app.data.MediaCategory
import com.flixbox.app.data.MediaRepository
import com.flixbox.app.ui.theme.*

// ── Add Media Screen (bottom sheet style fullscreen) ─────────────────────────
@Composable
fun AddMediaScreen(
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var mode by remember { mutableStateOf(AddMode.CHOOSE) }

    when (mode) {
        AddMode.CHOOSE -> ChooseModeDialog(
            onDismiss = onDismiss,
            onCreateFolder = { mode = AddMode.CREATE_FOLDER },
            onImportFile = { mode = AddMode.BROWSE }
        )
        AddMode.CREATE_FOLDER -> CreateFolderDialog(
            repository = repository,
            onDismiss = onDismiss,
            onDone = onDone
        )
        AddMode.BROWSE -> FileBrowserScreen(
            repository = repository,
            onDismiss = onDismiss,
            onDone = onDone
        )
    }
}

enum class AddMode { CHOOSE, CREATE_FOLDER, BROWSE }

// ── Mode Pilihan ──────────────────────────────────────────────────────────────
@Composable
fun ChooseModeDialog(
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onImportFile: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(FxDark, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Text("Tambah Konten", color = FxWhite, fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
            HorizontalDivider(color = FxCard)

            // Buat folder baru
            ListItem(
                headlineContent = { Text("Buat Folder Baru", color = FxWhite) },
                supportingContent = { Text("Buat folder kosong di dalam FlixBox", color = FxGray) },
                leadingContent = {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(FxRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.CreateNewFolder, null, tint = FxRed) }
                },
                modifier = Modifier.clickable { onCreateFolder() },
                colors = ListItemDefaults.colors(containerColor = FxDark)
            )

            // Import dari storage
            ListItem(
                headlineContent = { Text("Import dari Storage", color = FxWhite) },
                supportingContent = { Text("Browse & copy file/folder ke FlixBox", color = FxGray) },
                leadingContent = {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1565C0).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF42A5F5)) }
                },
                modifier = Modifier.clickable { onImportFile() },
                colors = ListItemDefaults.colors(containerColor = FxDark)
            )

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Batal", color = FxGray)
            }
        }
    }
}

// ── Create Folder Dialog ──────────────────────────────────────────────────────
@Composable
fun CreateFolderDialog(
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MediaCategory.ANIME) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(FxDark, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Text("Buat Folder Baru", color = FxWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = "" },
                label = { Text("Nama judul", color = FxGray) },
                singleLine = true,
                isError = error.isNotEmpty(),
                supportingText = if (error.isNotEmpty()) {{ Text(error, color = MaterialTheme.colorScheme.error) }} else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FxRed, unfocusedBorderColor = FxGray,
                    focusedTextColor = FxWhite, unfocusedTextColor = FxWhite, cursorColor = FxRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Kategori", color = FxGray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val cats = listOf(MediaCategory.ANIME, MediaCategory.MOVIE,
                MediaCategory.SERIES, MediaCategory.LIVE_ACTION)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cat ->
                            val sel = selectedCategory == cat
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) FxRed else FxCard)
                                    .border(
                                        width = if (sel) 0.dp else 1.dp,
                                        color = if (sel) FxRed else FxCard,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(cat.label, color = if (sel) FxWhite else FxLightGray,
                                    fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, FxGray)) {
                    Text("Batal", color = FxGray)
                }
                Button(
                    onClick = {
                        if (name.isBlank()) { error = "Nama tidak boleh kosong"; return@Button }
                        isLoading = true
                        val ok = repository.createFolder(name.trim(), selectedCategory)
                        isLoading = false
                        if (ok) onDone() else error = "Gagal buat folder. Periksa permission."
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FxRed),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = FxWhite, modifier = Modifier.size(18.dp))
                    else Text("Buat", color = FxWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── File Browser ──────────────────────────────────────────────────────────────
@Composable
fun FileBrowserScreen(
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(listOf<BrowserItem>()) }
    var pathStack by remember { mutableStateOf(listOf<String?>()) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentPath) {
        items = repository.listDirectory(currentPath)
    }

    // Import dialog
    if (showImportDialog && selectedItems.isNotEmpty()) {
        ImportConfigDialog(
            selectedPaths = selectedItems.toList(),
            repository = repository,
            onDismiss = { showImportDialog = false },
            onDone = {
                showImportDialog = false
                onDone()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(FxBlack)) {

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (pathStack.isEmpty()) onDismiss()
                else {
                    val prev = pathStack.last()
                    pathStack = pathStack.dropLast(1)
                    currentPath = prev
                }
            }) {
                Icon(Icons.Default.ArrowBack, null, tint = FxWhite)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("File Browser", color = FxWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    currentPath?.substringAfterLast("/") ?: "Root Storage",
                    color = FxGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (selectedItems.isNotEmpty()) {
                Button(
                    onClick = { showImportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = FxRed),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Import (${selectedItems.size})", color = FxWhite, fontSize = 13.sp)
                }
            }
        }

        // Breadcrumb
        currentPath?.let { path ->
            val parts = path.split("/").filter { it.isNotEmpty() }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Home, null, tint = FxGray, modifier = Modifier.size(14.dp))
                parts.forEachIndexed { i, part ->
                    Text("/", color = FxGray, fontSize = 11.sp)
                    Text(part, color = if (i == parts.lastIndex) FxWhite else FxGray, fontSize = 11.sp)
                }
            }
        }

        HorizontalDivider(color = FxCard)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FxRed)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                items(items) { item ->
                    BrowserItemRow(
                        item = item,
                        isSelected = item.path in selectedItems,
                        onTap = {
                            if (item.isDirectory) {
                                pathStack = pathStack + listOf(currentPath)
                                currentPath = item.path
                            } else if (item.isVideoFile) {
                                selectedItems = if (item.path in selectedItems)
                                    selectedItems - item.path
                                else selectedItems + item.path
                            }
                        },
                        onLongPress = {
                            selectedItems = if (item.path in selectedItems)
                                selectedItems - item.path
                            else selectedItems + item.path
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserItemRow(
    item: BrowserItem,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val bg = if (isSelected) FxRed.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        val icon = when {
            item.isRoot -> Icons.Default.Storage
            item.isDirectory -> Icons.Default.Folder
            item.isVideoFile -> Icons.Default.VideoFile
            else -> Icons.Default.InsertDriveFile
        }
        val iconTint = when {
            isSelected -> FxRed
            item.isRoot -> FxLightGray
            item.isDirectory -> Color(0xFFFFA726)
            item.isVideoFile -> Color(0xFF42A5F5)
            else -> FxGray
        }
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = if (isSelected) FxWhite else FxLightGray,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            if (!item.isDirectory && item.size > 0) {
                Text(formatSize(item.size), color = FxGray, fontSize = 11.sp)
            }
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, null, tint = FxRed, modifier = Modifier.size(20.dp))
        } else if (item.isDirectory) {
            Icon(Icons.Default.ChevronRight, null, tint = FxGray, modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(color = FxCard.copy(alpha = 0.5f))
}

// ── Import Config Dialog ──────────────────────────────────────────────────────
@Composable
fun ImportConfigDialog(
    selectedPaths: List<String>,
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var titleName by remember {
        mutableStateOf(
            selectedPaths.firstOrNull()?.substringAfterLast("/")
                ?.substringBeforeLast(".") ?: ""
        )
    }
    var selectedCategory by remember { mutableStateOf(MediaCategory.ANIME) }
    var isImporting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(Pair(0, 0)) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!isImporting) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(FxDark, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Text("Import ke FlixBox", color = FxWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${selectedPaths.size} item dipilih", color = FxGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = titleName,
                onValueChange = { titleName = it },
                label = { Text("Nama judul", color = FxGray) },
                singleLine = true,
                enabled = !isImporting,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FxRed, unfocusedBorderColor = FxGray,
                    focusedTextColor = FxWhite, unfocusedTextColor = FxWhite, cursorColor = FxRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Kategori", color = FxGray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val cats = listOf(MediaCategory.ANIME, MediaCategory.MOVIE,
                MediaCategory.SERIES, MediaCategory.LIVE_ACTION)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.take(2).forEach { cat ->
                    val sel = selectedCategory == cat
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (sel) FxRed else FxCard)
                        .clickable(enabled = !isImporting) { selectedCategory = cat }
                        .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(cat.label, color = if (sel) FxWhite else FxGray, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.drop(2).forEach { cat ->
                    val sel = selectedCategory == cat
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (sel) FxRed else FxCard)
                        .clickable(enabled = !isImporting) { selectedCategory = cat }
                        .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(cat.label, color = if (sel) FxWhite else FxGray, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }

            if (isImporting) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = FxRed, trackColor = FxCard
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Menyalin ${progress.first}/${progress.second} file...",
                    color = FxGray, fontSize = 12.sp)
            }

            if (error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    enabled = !isImporting, border = BorderStroke(1.dp, FxGray)) {
                    Text("Batal", color = FxGray)
                }

                val scope = androidx.compose.runtime.rememberCoroutineScope()
                Button(
                    onClick = {
                        if (titleName.isBlank()) { error = "Nama tidak boleh kosong"; return@Button }
                        isImporting = true
                        error = ""
                        scope.launch {
                            var allOk = true
                            selectedPaths.forEach { path ->
                                val ok = repository.importToFlixBox(
                                    sourcePath = path,
                                    category = selectedCategory,
                                    titleName = titleName.trim()
                                ) { done, total -> progress = Pair(done, total) }
                                if (!ok) allOk = false
                            }
                            isImporting = false
                            if (allOk) onDone() else error = "Beberapa file gagal disalin."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FxRed),
                    enabled = !isImporting
                ) {
                    if (isImporting) CircularProgressIndicator(color = FxWhite, modifier = Modifier.size(18.dp))
                    else Text("Import", color = FxWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

// coroutine scope helper untuk Button
