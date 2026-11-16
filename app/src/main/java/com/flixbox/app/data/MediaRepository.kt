package com.flixbox.app.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("FlixBoxPrefs", Context.MODE_PRIVATE)

    companion object {
        const val ROOT_FOLDER_NAME = ".FlixBox"
        val VIDEO_EXTENSIONS = listOf("mp4", "mkv", "avi", "mov", "webm")
        val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
        val MEDIA_EXTENSIONS = VIDEO_EXTENSIONS + IMAGE_EXTENSIONS
        // Thumbnail didekode kecil, BUKAN resolusi asli video. Bitmap 1080p penuh ~8MB;
        // di ukuran ini ~0.5MB — cache jadi muat puluhan thumbnail, bukan cuma ~4 biji
        // sebelum ke-evict dan memicu decode ulang video tiap scroll (penyebab utama "berat").
        private const val THUMB_TARGET_W = 480
        private const val THUMB_TARGET_H = 270
    }

    // ── Root dirs ─────────────────────────────────────────────────────────────
    private fun getRootDirs(): List<File> {
        val dirs = mutableListOf<File>()
        val internal = File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME)
        if (!internal.exists()) internal.mkdirs()
        dirs.add(internal)
        try {
            context.getExternalFilesDirs(null).forEach { appDir ->
                if (appDir != null) {
                    var root = appDir
                    repeat(4) { root = root.parentFile ?: root }
                    val ext = File(root, ROOT_FOLDER_NAME)
                    if (ext.exists() && ext.absolutePath != internal.absolutePath) dirs.add(ext)
                }
            }
        } catch (e: Exception) { }
        return dirs
    }

    // ── Thumbnail cache ───────────────────────────────────────────────────────
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val thumbnailCache = object : LruCache<String, Bitmap>(maxMemory / 8) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    // Batasi max 3 thumbnail load bersamaan
    private val thumbnailSemaphore = Semaphore(3)
    private val loadMutex = Mutex()
    private val loadingPaths = mutableSetOf<String>()

    // Ambil dari cache — null kalau belum ada (tidak trigger load)
    fun getCachedThumbnail(path: String): Bitmap? = thumbnailCache.get(path)

    suspend fun getThumbnail(path: String, timeUs: Long = 30_000_000L): Bitmap? {
        thumbnailCache.get(path)?.let { return it }
        loadMutex.withLock {
            if (path in loadingPaths) return null
            loadingPaths.add(path)
        }
        return try {
            thumbnailSemaphore.withPermit {
                withContext(Dispatchers.IO) {
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(path)
                        val bmp = if (Build.VERSION.SDK_INT >= 27) {
                            // API 27+: retriever langsung decode di ukuran kecil (lebih hemat & cepat)
                            r.getScaledFrameAtTime(
                                timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                THUMB_TARGET_W, THUMB_TARGET_H
                            )
                        } else {
                            // Di bawah API 27 tidak ada versi scaled — decode penuh dulu, baru
                            // diperkecil, lalu bitmap aslinya langsung dibuang dari memory
                            r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?.let { full ->
                                    val scaled = Bitmap.createScaledBitmap(full, THUMB_TARGET_W, THUMB_TARGET_H, true)
                                    if (scaled !== full) full.recycle()
                                    scaled
                                }
                        }
                        r.release()
                        if (bmp != null) thumbnailCache.put(path, bmp)
                        bmp
                    } catch (e: Exception) { null }
                }
            }
        } finally {
            loadMutex.withLock { loadingPaths.remove(path) }
        }
    }

    // Preload semua thumbnail sekaligus (dipanggil dari DetailScreen)
    suspend fun preloadThumbnails(paths: List<String>) {
        paths.forEach { path ->
            if (thumbnailCache.get(path) == null) {
                getThumbnail(path)
            }
        }
    }

    // Bersihkan cache thumbnail & durasi di memory. Dipakai tombol "Bersihkan Cache" di
    // Pengaturan, dan berguna kalau ada thumbnail basi nyangkut setelah rename/hapus folder
    // (cache sebelumnya tidak pernah diinvalidasi per path saat rename/delete).
    fun clearThumbnailCache() {
        thumbnailCache.evictAll()
        durationCache.clear()
    }

    // ── Duration cache ────────────────────────────────────────────────────────
    private val durationCache = mutableMapOf<String, Long>()

    private fun getDuration(path: String): Long {
        durationCache[path]?.let { return it }
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(path)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            r.release()
            durationCache[path] = d
            d
        } catch (e: Exception) { 0L }
    }

    // ── Video file helpers ────────────────────────────────────────────────────
    private fun getVideoFiles(folder: File): List<File> =
        folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
            ?.sortedWith(compareBy({ extractEpNumber(it.name) }, { it.name }))
            ?: emptyList()

    // ── getEpisodes — instant, tanpa durasi ───────────────────────────────────
    suspend fun getEpisodes(folderPath: String): List<EpisodeModel> = withContext(Dispatchers.IO) {
        getVideoFiles(File(folderPath)).mapIndexed { i, file ->
            EpisodeModel(
                title = file.nameWithoutExtension,
                path = file.absolutePath,
                duration = durationCache[file.absolutePath] ?: 0L,
                size = file.length(),
                episodeNumber = i + 1
            )
        }
    }

    // ── loadDurationsFlow — update durasi incremental ─────────────────────────
    fun loadDurationsFlow(episodes: List<EpisodeModel>): Flow<List<EpisodeModel>> = flow {
        val updated = episodes.toMutableList()
        emit(updated.toList())
        updated.forEachIndexed { i, ep ->
            if (ep.duration == 0L) {
                val d = getDuration(ep.path)
                if (d > 0L) {
                    updated[i] = ep.copy(duration = d)
                    emit(updated.toList())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── getSeasons — instant, tanpa durasi ────────────────────────────────────
    suspend fun getSeasons(folderPath: String): List<SeasonModel> = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        val seasonFolders = folder.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedWith(compareBy({ extractSeasonNumber(it.name) }, { it.name }))
            ?: return@withContext emptyList()

        seasonFolders.map { sf ->
            SeasonModel(
                name = sf.name,
                folderPath = sf.absolutePath,
                episodes = getVideoFiles(sf).mapIndexed { i, file ->
                    EpisodeModel(
                        title = file.nameWithoutExtension,
                        path = file.absolutePath,
                        duration = durationCache[file.absolutePath] ?: 0L,
                        size = file.length(),
                        episodeNumber = i + 1
                    )
                },
                posterPath = getPosterPath(sf)
            )
        }
    }

    // ── loadSeasonDurationsFlow ───────────────────────────────────────────────
    fun loadSeasonDurationsFlow(seasons: List<SeasonModel>): Flow<List<SeasonModel>> = flow {
        val updated = seasons.toMutableList()
        emit(updated.toList())
        updated.forEachIndexed { si, season ->
            val eps = season.episodes.toMutableList()
            var changed = false
            eps.forEachIndexed { ei, ep ->
                if (ep.duration == 0L) {
                    val d = getDuration(ep.path)
                    if (d > 0L) { eps[ei] = ep.copy(duration = d); changed = true }
                }
            }
            if (changed) {
                updated[si] = season.copy(episodes = eps)
                emit(updated.toList())
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── getAllMedia ───────────────────────────────────────────────────────────
    suspend fun getAllMedia(): List<MediaModel> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaModel>()
        val seen = mutableSetOf<String>()
        getRootDirs().forEach { rootDir ->
            MediaCategory.values().filter { it != MediaCategory.ALL }.forEach { category ->
                val catDir = File(rootDir, category.folderName)
                if (!catDir.exists()) catDir.mkdirs()
                catDir.listFiles()?.sortedBy { it.name }?.forEach { folder ->
                    if (folder.isDirectory && folder.absolutePath !in seen) {
                        scanTitleFolder(folder, category)?.let {
                            seen.add(folder.absolutePath)
                            result.add(it)
                        }
                    }
                }
            }
        }
        result
    }

    // Satu kali listFiles() per folder judul + satu kali per folder season-nya.
    // Sebelumnya countTotalEpisodes(), getFirstEpisodePath(), getPosterPath(), dan
    // hasSeasonSubfolders() masing-masing listFiles() SENDIRI-SENDIRI untuk folder yang
    // sama (4+ panggilan filesystem per judul, lebih lagi untuk season), dan ini dipanggil
    // ulang tiap kali Home dibuka — makin berat makin banyak judul/season di koleksi.
    private fun scanTitleFolder(folder: File, category: MediaCategory): MediaModel? {
        val children = folder.listFiles() ?: return null
        val seasonFolders = children.filter { it.isDirectory }
            .sortedWith(compareBy({ extractSeasonNumber(it.name) }, { it.name }))
        val hasSeasons = seasonFolders.isNotEmpty()

        val totalEpisodes: Int
        val firstEpisodePath: String
        if (hasSeasons) {
            var total = 0
            var first = ""
            seasonFolders.forEach { sf ->
                val eps = getVideoFiles(sf)
                total += eps.size
                if (first.isEmpty() && eps.isNotEmpty()) first = eps.first().absolutePath
            }
            totalEpisodes = total
            firstEpisodePath = first
        } else {
            val eps = getVideoFiles(folder)
            totalEpisodes = eps.size
            firstEpisodePath = eps.firstOrNull()?.absolutePath ?: ""
        }
        if (totalEpisodes == 0) return null

        val posterPath = children.firstOrNull {
            it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS
        }?.absolutePath ?: ""

        return MediaModel(
            title = folder.name,
            folderPath = folder.absolutePath,
            episodeCount = totalEpisodes,
            firstEpisodePath = firstEpisodePath,
            category = category,
            posterPath = posterPath,
            hasSeasons = hasSeasons
        )
    }

    // ── File browser ──────────────────────────────────────────────────────────
    fun listDirectory(path: String?): List<BrowserItem> {
        if (path == null) {
            val roots = mutableListOf<BrowserItem>()
            val internal = Environment.getExternalStorageDirectory()
            roots.add(BrowserItem(internal.absolutePath, "Penyimpanan Internal", true, isRoot = true))
            try {
                context.getExternalFilesDirs(null).forEach { appDir ->
                    if (appDir != null) {
                        var root = appDir
                        repeat(4) { root = root.parentFile ?: root }
                        if (root.absolutePath != internal.absolutePath)
                            roots.add(BrowserItem(root.absolutePath, "Penyimpanan Eksternal", true, isRoot = true))
                    }
                }
            } catch (e: Exception) { }
            return roots
        }
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.canRead() }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { f ->
                BrowserItem(
                    path = f.absolutePath,
                    name = f.name,
                    isDirectory = f.isDirectory,
                    isVideoFile = !f.isDirectory && f.extension.lowercase() in VIDEO_EXTENSIONS,
                    size = if (f.isFile) f.length() else 0L
                )
            } ?: emptyList()
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────
    fun createFolder(name: String, category: MediaCategory, rootIndex: Int = 0): Boolean {
        val roots = getRootDirs()
        val root = roots.getOrElse(rootIndex) { roots.first() }
        return File(File(root, category.folderName), name).mkdirs()
    }

    // Rangkuman hasil satu panggilan importToFlixBox — dipakai UI untuk tampilkan ringkasan
    data class ImportResult(
        val totalFiles: Int = 0,
        val copiedFiles: Int = 0,
        val skippedFiles: Int = 0,   // file identik (ukuran sama) sudah ada di tujuan → dilewati
        val renamedFiles: Int = 0,   // nama bentrok tapi isi beda → disalin dengan nama baru
        val failedFiles: Int = 0,
        val cancelled: Boolean = false
    )

    private enum class CopyOutcome { COPIED, SKIPPED, RENAMED, FAILED }

    // Counter mutable yang dibagi sepanjang rekursi (hindari banyak return value bertingkat)
    private class ImportCounters {
        var copied = 0; var skipped = 0; var renamed = 0; var failed = 0; var done = 0
    }

    // Hitung total file media (video+gambar) secara rekursif — dipakai untuk progress bar akurat
    private fun countImportableFiles(source: File): Int {
        if (!source.exists()) return 0
        if (source.isFile) return if (source.extension.lowercase() in MEDIA_EXTENSIONS) 1 else 0
        var count = 0
        source.listFiles()?.forEach { f ->
            count += if (f.isDirectory) countImportableFiles(f)
            else if (f.extension.lowercase() in MEDIA_EXTENSIONS) 1 else 0
        }
        return count
    }

    // Versi publik — dipakai UI untuk hitung total sebelum mulai import (progress gabungan multi-item)
    suspend fun countMediaFiles(path: String): Int = withContext(Dispatchers.IO) {
        countImportableFiles(File(path))
    }

    // Salin satu file ke folder tujuan tanpa pernah menimpa file yang sudah ada:
    // - ukuran sama → dianggap file yang sama, dilewati (SKIPPED)
    // - ukuran beda tapi nama sama → disalin dengan nama baru "nama (1).ext" dst (RENAMED)
    private fun copyWithConflictHandling(src: File, destDir: File): CopyOutcome {
        destDir.mkdirs()
        var target = File(destDir, src.name)
        if (target.exists()) {
            if (target.length() == src.length()) return CopyOutcome.SKIPPED
            val base = src.nameWithoutExtension
            val ext = src.extension
            var n = 1
            while (target.exists()) {
                target = File(destDir, if (ext.isNotEmpty()) "$base ($n).$ext" else "$base ($n)")
                n++
            }
            return try {
                src.copyTo(target, overwrite = false); CopyOutcome.RENAMED
            } catch (e: Exception) { CopyOutcome.FAILED }
        }
        return try {
            src.copyTo(target, overwrite = false); CopyOutcome.COPIED
        } catch (e: Exception) { CopyOutcome.FAILED }
    }

    // Salin ISI srcDir ke destDir secara rekursif, mempertahankan nama subfolder apa adanya
    // (jadi struktur "Season 1/", "Season 2/" dst ikut terbawa persis). isCancelled dicek
    // di antar-file sehingga tombol "Batalkan" di UI berhenti secepat file yang sedang
    // berjalan selesai disalin, bukan harus menunggu seluruh folder selesai.
    private fun copyContentsRecursive(
        srcDir: File, destDir: File, counters: ImportCounters, total: Int,
        onProgress: (Int, Int) -> Unit, isCancelled: () -> Boolean
    ) {
        val children = srcDir.listFiles()?.sortedBy { it.name } ?: return
        for (child in children) {
            if (isCancelled()) return
            when {
                child.isDirectory -> {
                    copyContentsRecursive(child, File(destDir, child.name), counters, total, onProgress, isCancelled)
                }
                child.extension.lowercase() in MEDIA_EXTENSIONS -> {
                    when (copyWithConflictHandling(child, destDir)) {
                        CopyOutcome.COPIED -> counters.copied++
                        CopyOutcome.SKIPPED -> counters.skipped++
                        CopyOutcome.RENAMED -> counters.renamed++
                        CopyOutcome.FAILED -> counters.failed++
                    }
                    counters.done++
                    onProgress(counters.done, total)
                }
            }
        }
    }

    // ── Import rekursif ke FlixBox (P1) ───────────────────────────────────────
    // sourcePath boleh berupa folder (disalin rekursif, struktur season dipertahankan)
    // atau satu file video. onProgress melapor (fileSelesai, totalFile) — total dihitung
    // lebih dulu secara rekursif agar progress bar akurat untuk folder bersarang.
    // isCancelled dicek berkala; saat true, proses berhenti di titik aman berikutnya dan
    // hasil sebagian yang sudah tersalin TETAP ada (tidak di-rollback).
    suspend fun importToFlixBox(
        sourcePath: String, category: MediaCategory, titleName: String,
        rootIndex: Int = 0,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): ImportResult = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        if (!source.exists()) return@withContext ImportResult()

        val total = countImportableFiles(source)
        if (total == 0) return@withContext ImportResult()

        val roots = getRootDirs()
        val root = roots.getOrElse(rootIndex) { roots.first() }
        val dest = File(File(root, category.folderName), titleName).also { it.mkdirs() }

        val counters = ImportCounters()
        if (source.isDirectory) {
            copyContentsRecursive(source, dest, counters, total, onProgress, isCancelled)
        } else if (source.extension.lowercase() in MEDIA_EXTENSIONS && !isCancelled()) {
            when (copyWithConflictHandling(source, dest)) {
                CopyOutcome.COPIED -> counters.copied++
                CopyOutcome.SKIPPED -> counters.skipped++
                CopyOutcome.RENAMED -> counters.renamed++
                CopyOutcome.FAILED -> counters.failed++
            }
            counters.done++
            onProgress(counters.done, total)
        }

        ImportResult(
            totalFiles = total,
            copiedFiles = counters.copied,
            skippedFiles = counters.skipped,
            renamedFiles = counters.renamed,
            failedFiles = counters.failed,
            cancelled = isCancelled() && counters.done < total
        )
    }

    fun renameFolder(oldPath: String, newName: String): Boolean {
        val old = File(oldPath)
        return old.renameTo(File(old.parent ?: return false, newName))
    }
    fun deleteFolder(path: String): Boolean = File(path).deleteRecursively()
    fun deleteFile(path: String): Boolean = File(path).delete()

    // ── Watch progress ────────────────────────────────────────────────────────
    fun getWatchedPosition(path: String): Long = prefs.getLong("pos_$path", 0L)
    fun saveWatchedPosition(path: String, position: Long) {
        if (path.isEmpty() || position < 0) return
        prefs.edit().putLong("pos_$path", position).apply()
    }
    fun getRecentlyWatched(): List<Pair<String, Long>> {
        val raw = prefs.getString("recently_watched", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("|").filter { it.isNotEmpty() }.mapNotNull {
            try { val p = it.split(","); if (p.size >= 2) Pair(p[0], p[1].toLong()) else null }
            catch (e: Exception) { null }
        }
    }
    fun saveRecentlyWatched(path: String) {
        if (path.isEmpty()) return
        val list = getRecentlyWatched().toMutableList()
        list.removeAll { it.first == path }
        list.add(0, Pair(path, System.currentTimeMillis()))
        prefs.edit().putString("recently_watched",
            list.take(20).joinToString("|") { "${it.first},${it.second}" }).apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    fun getPosterPath(folder: File): String =
        folder.listFiles()?.firstOrNull {
            it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS
        }?.absolutePath ?: ""

    private fun extractSeasonNumber(name: String): Int {
        val m = Regex("""[Ss]eason\s*(\d+)|[Ss](\d+)""").find(name)
        return m?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 999
    }

    private fun extractEpNumber(name: String): Int {
        val m = Regex("""[eE](\d+)|[sS]\d+[eE](\d+)|[-.\s](\d+)[-.\s]|^(\d+)""").find(name)
        return m?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: Int.MAX_VALUE
    }
}

data class BrowserItem(
    val path: String, val name: String, val isDirectory: Boolean,
    val isVideoFile: Boolean = false, val isRoot: Boolean = false, val size: Long = 0L
)
