package com.flixbox.app.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
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
        // .srt, .vtt, .ass/.ssa — ketiganya didukung native oleh Media3, tidak perlu
        // dependency tambahan (ASS/SSA cuma dukungan "minimal": teks & timing kebaca
        // benar, styling/posisi custom ala fansub kemungkinan tidak ikut ter-render)
        val SUBTITLE_EXTENSIONS = listOf("srt", "vtt", "ass", "ssa")
        // Thumbnail didekode kecil, BUKAN resolusi asli video. Bitmap 1080p penuh ~8MB;
        // di ukuran ini ~0.5MB — cache jadi muat puluhan thumbnail, bukan cuma ~4 biji
        // sebelum ke-evict dan memicu decode ulang video tiap scroll (penyebab utama "berat").
        private const val THUMB_TARGET_W = 480
        private const val THUMB_TARGET_H = 270
        // Episode dianggap "selesai ditonton" kalau sisa tontonan tinggal segini atau
        // kurang (buat nolerir durasi credit/outro di akhir video)
        private const val WATCHED_COMPLETE_THRESHOLD_MS = 15_000L
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
    // Dua lapis: memory (LruCache, cepat tapi hilang kalau app di-kill) dan disk
    // (context.cacheDir/thumbs, bertahan antar sesi). Sebelumnya cuma ada memory cache,
    // jadi SETIAP kali app dibuka ulang / Home di-load lagi dari kondisi dingin, semua
    // thumbnail didekode ulang dari video dari nol — makin banyak judul, makin kerasa
    // "muncul tapi lama/lag". Dengan disk cache, cuma didekode dari video SEKALI seumur
    // file (sampai cache dibersihkan manual atau filenya diganti).
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val thumbnailCache = object : LruCache<String, Bitmap>(maxMemory / 8) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    // Batasi max 3 decode VIDEO bersamaan (operasi berat) — baca dari disk cache
    // TIDAK lewat semaphore ini karena jauh lebih ringan, lihat getThumbnail() di bawah.
    private val thumbnailSemaphore = Semaphore(3)
    private val loadMutex = Mutex()
    // Dulu pakai Set<String> dan request yang "nyusul" langsung dikasih null permanen
    // (tidak pernah dicoba ulang) — kalau 2 tempat di UI minta thumbnail yang sama
    // bersamaan, salah satunya nyangkut shimmer selamanya walau yang satu lagi berhasil.
    // Sekarang request yang nyusul ikut nunggu hasil yang lagi jalan lewat Deferred ini.
    private val inFlightThumbnails = mutableMapOf<String, CompletableDeferred<Bitmap?>>()

    // Ambil dari cache — null kalau belum ada (tidak trigger load)
    fun getCachedThumbnail(path: String): Bitmap? = thumbnailCache.get(path)

    private fun thumbnailDiskCacheFile(path: String): File {
        val hash = java.security.MessageDigest.getInstance("MD5").digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "thumbs/$hash.jpg")
    }

    private fun loadThumbnailFromDisk(path: String): Bitmap? {
        val f = thumbnailDiskCacheFile(path)
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.absolutePath) } catch (e: Exception) { null }
    }

    private fun saveThumbnailToDisk(path: String, bmp: Bitmap) {
        try {
            val f = thumbnailDiskCacheFile(path)
            f.parentFile?.mkdirs()
            f.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        } catch (e: Exception) { /* gagal nulis cache bukan fatal, cuma berarti decode lagi nanti */ }
    }

    private fun decodeThumbnailFromVideo(path: String, timeUs: Long): Bitmap? = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val bmp = if (Build.VERSION.SDK_INT >= 27) {
            // API 27+: retriever langsung decode di ukuran kecil (lebih hemat & cepat)
            r.getScaledFrameAtTime(
                timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_TARGET_W, THUMB_TARGET_H
            )
        } else {
            // Di bawah API 27 tidak ada versi scaled — decode penuh dulu, baru diperkecil,
            // lalu bitmap aslinya langsung dibuang dari memory
            r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                val scaled = Bitmap.createScaledBitmap(full, THUMB_TARGET_W, THUMB_TARGET_H, true)
                if (scaled !== full) full.recycle()
                scaled
            }
        }
        r.release()
        bmp
    } catch (e: Exception) { null }

    suspend fun getThumbnail(path: String, timeUs: Long = 30_000_000L): Bitmap? {
        thumbnailCache.get(path)?.let { return it }

        // Request yang sama lagi jalan di tempat lain? Tunggu hasilnya, jangan decode dobel
        // dan jangan langsung balikin null.
        val waiting = loadMutex.withLock { inFlightThumbnails[path] }
        if (waiting != null) return waiting.await()

        val deferred = CompletableDeferred<Bitmap?>()
        loadMutex.withLock { inFlightThumbnails[path] = deferred }

        val result = withContext(Dispatchers.IO) {
            val fromDisk = loadThumbnailFromDisk(path)
            if (fromDisk != null) {
                thumbnailCache.put(path, fromDisk)
                fromDisk
            } else {
                // Cuma decode video (berat) yang antre lewat semaphore — baca disk cache
                // di atas sengaja TIDAK ikut antre karena jauh lebih ringan/cepat.
                thumbnailSemaphore.withPermit {
                    val decoded = decodeThumbnailFromVideo(path, timeUs)
                    if (decoded != null) {
                        thumbnailCache.put(path, decoded)
                        saveThumbnailToDisk(path, decoded)
                    }
                    decoded
                }
            }
        }

        loadMutex.withLock { inFlightThumbnails.remove(path) }
        deferred.complete(result)
        return result
    }

    // Preload semua thumbnail sekaligus (dipanggil dari DetailScreen)
    suspend fun preloadThumbnails(paths: List<String>) {
        paths.forEach { path ->
            if (thumbnailCache.get(path) == null) {
                getThumbnail(path)
            }
        }
    }

    // Bersihkan cache thumbnail (memory + disk) & durasi. Dipakai tombol "Bersihkan Cache"
    // di Pengaturan, dan berguna kalau ada thumbnail basi nyangkut setelah rename/hapus
    // folder (cache sebelumnya — dan disk cache yang baru ini juga — tidak diinvalidasi
    // otomatis per path saat rename/delete).
    fun clearThumbnailCache() {
        thumbnailCache.evictAll()
        durationCache.clear()
        try { File(context.cacheDir, "thumbs").deleteRecursively() } catch (e: Exception) { }
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

    // duration opsional: kalau diisi dan posisi sudah dekat akhir (sisa < 15 detik),
    // episode ditandai selesai ditonton (P2, bagian terakhir yang kemarin belum).
    // Sekali ditandai selesai, statusnya tetap nempel walau nanti di-rewind untuk
    // nonton ulang sebagian — sama seperti badge "sudah ditonton" di Netflix dkk.
    fun saveWatchedPosition(path: String, position: Long, duration: Long = 0L) {
        if (path.isEmpty() || position < 0) return
        prefs.edit().putLong("pos_$path", position).apply()
        if (duration > 0 && position >= duration - WATCHED_COMPLETE_THRESHOLD_MS) {
            prefs.edit().putBoolean("done_$path", true).apply()
        }
    }

    fun isWatchedComplete(path: String): Boolean = prefs.getBoolean("done_$path", false)
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

    // ── Subtitle (P3) ─────────────────────────────────────────────────────────
    // Pairing video↔subtitle: cocokkan berdasarkan NAMA FILE yang sama (atau nama sama
    // + kode bahasa, misal "Episode 01.id.srt"), karena itu satu-satunya cara yang bisa
    // diandalkan tanpa baca isi file. Kalau namanya beda, user attach manual sekali lewat
    // attachSubtitle() — yang nyalin file itu supaya namanya ikut cocok, jadi lain kali
    // otomatis ketemu lagi tanpa perlu pilih ulang.
    fun findSubtitlesForEpisode(episodePath: String): List<File> {
        val video = File(episodePath)
        val baseName = video.nameWithoutExtension
        val dir = video.parentFile ?: return emptyList()
        return dir.listFiles()?.filter { f ->
            f.isFile && f.extension.lowercase() in SUBTITLE_EXTENSIONS &&
                (f.nameWithoutExtension == baseName || f.nameWithoutExtension.startsWith("$baseName."))
        }?.sortedBy { it.name } ?: emptyList()
    }

    // Salin file subtitle yang dipilih manual supaya namanya cocok dengan video (base name
    // sama), lalu ditaruh di folder yang sama persis — otomatis kedetect lagi lain kali oleh
    // findSubtitlesForEpisode(). Overwrite kalau sebelumnya sudah pernah attach manual juga
    // (saat ini cuma 1 subtitle manual per episode; subtitle hasil auto-detect dengan nama
    // berbeda tetap aman, tidak ketimpa).
    fun attachSubtitle(episodePath: String, pickedFile: File): File? {
        val video = File(episodePath)
        val dir = video.parentFile ?: return null
        val target = File(dir, "${video.nameWithoutExtension}.${pickedFile.extension.lowercase()}")
        return try {
            pickedFile.copyTo(target, overwrite = true)
            target
        } catch (e: Exception) { null }
    }

    fun getSubtitleDelayMs(episodePath: String): Long = prefs.getLong("subdelay_$episodePath", 0L)
    fun saveSubtitleDelayMs(episodePath: String, delayMs: Long) {
        prefs.edit().putLong("subdelay_$episodePath", delayMs).apply()
    }

    fun subtitleMimeType(file: File): String = when (file.extension.lowercase()) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "text/x-ssa"
    }

    // Kalau delayMs == 0, pakai file subtitle aslinya langsung. Kalau tidak, bikin salinan
    // di cache dengan timestamp digeser (ExoPlayer tidak punya API delay/offset langsung
    // buat subtitle eksternal, jadi caranya adalah menulis ulang waktu tampilnya).
    fun prepareSubtitleForPlayback(subtitleFile: File, delayMs: Long): File {
        if (delayMs == 0L) return subtitleFile
        return try {
            val ext = subtitleFile.extension.lowercase()
            val content = subtitleFile.readText()
            val shifted = if (ext == "ass" || ext == "ssa") shiftAssTimestamps(content, delayMs)
                else shiftSrtVttTimestamps(content, delayMs)
            val out = File(context.cacheDir, "subs/${subtitleFile.nameWithoutExtension}_shifted.$ext")
            out.parentFile?.mkdirs()
            out.writeText(shifted)
            out
        } catch (e: Exception) { subtitleFile }
    }

    // Format SRT/VTT: 00:01:23,456 (SRT pakai koma) atau 00:01:23.456 (VTT pakai titik)
    private val srtVttTimeRegex = Regex("""(\d{1,2}):(\d{2}):(\d{2})([.,])(\d{3})""")
    private fun shiftSrtVttTimestamps(content: String, delayMs: Long): String =
        srtVttTimeRegex.replace(content) { m ->
            val (h, mi, s) = Triple(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLong())
            val sep = m.groupValues[4]
            val ms = m.groupValues[5].toLong()
            var total = (((h * 60 + mi) * 60 + s) * 1000 + ms) + delayMs
            if (total < 0) total = 0
            val nh = total / 3_600_000; total %= 3_600_000
            val nm = total / 60_000; total %= 60_000
            val ns = total / 1000; val nms = total % 1000
            "%02d:%02d:%02d%s%03d".format(nh, nm, ns, sep, nms)
        }

    // Format ASS/SSA: 0:01:23.45 (centisecond, 2 digit, jam cuma 1 digit)
    private val assTimeRegex = Regex("""(\d{1,2}):(\d{2}):(\d{2})\.(\d{2})""")
    private fun shiftAssTimestamps(content: String, delayMs: Long): String =
        assTimeRegex.replace(content) { m ->
            val (h, mi, s) = Triple(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLong())
            val cs = m.groupValues[4].toLong()
            var total = (((h * 60 + mi) * 60 + s) * 1000 + cs * 10) + delayMs
            if (total < 0) total = 0
            val nh = total / 3_600_000; total %= 3_600_000
            val nm = total / 60_000; total %= 60_000
            val ns = total / 1000; val ncs = (total % 1000) / 10
            "%d:%02d:%02d.%02d".format(nh, nm, ns, ncs)
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
