package com.flixbox.app.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
                        val bmp = r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
                        val total = countTotalEpisodes(folder)
                        if (total > 0) {
                            seen.add(folder.absolutePath)
                            result.add(MediaModel(
                                title = folder.name,
                                folderPath = folder.absolutePath,
                                episodeCount = total,
                                firstEpisodePath = getFirstEpisodePath(folder),
                                category = category,
                                posterPath = getPosterPath(folder),
                                hasSeasons = hasSeasonSubfolders(folder)
                            ))
                        }
                    }
                }
            }
        }
        result
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

    suspend fun importToFlixBox(
        sourcePath: String, category: MediaCategory, titleName: String,
        rootIndex: Int = 0, onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val roots = getRootDirs()
            val root = roots.getOrElse(rootIndex) { roots.first() }
            val dest = File(File(root, category.folderName), titleName).also { it.mkdirs() }
            val source = File(sourcePath)
            if (source.isDirectory) {
                val files = (source.listFiles()?.filter {
                    it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS + IMAGE_EXTENSIONS
                } ?: emptyList())
                files.forEachIndexed { i, f ->
                    onProgress(i + 1, files.size)
                    f.copyTo(File(dest, f.name), overwrite = true)
                }
            } else {
                onProgress(1, 1)
                source.copyTo(File(dest, source.name), overwrite = true)
            }
            true
        } catch (e: Exception) { false }
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

    private fun hasSeasonSubfolders(folder: File) =
        folder.listFiles()?.any { it.isDirectory } == true

    private fun getFirstEpisodePath(folder: File): String {
        val seasonFolders = folder.listFiles()?.filter { it.isDirectory }
            ?.sortedWith(compareBy({ extractSeasonNumber(it.name) }, { it.name }))
        if (!seasonFolders.isNullOrEmpty()) {
            val first = getVideoFiles(seasonFolders.first())
            if (first.isNotEmpty()) return first.first().absolutePath
        }
        return getVideoFiles(folder).firstOrNull()?.absolutePath ?: ""
    }

    private fun countTotalEpisodes(folder: File): Int {
        val seasons = folder.listFiles()?.filter { it.isDirectory }
        return if (!seasons.isNullOrEmpty()) seasons.sumOf { getVideoFiles(it).size }
        else getVideoFiles(folder).size
    }

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
