# 🎬 AnimeFlix — Offline Anime Streaming App

> Aplikasi Android streaming anime lokal yang dibuat 100% dari HP menggunakan AndroidIDE.  
> Tanpa laptop. Tanpa cloud. Tanpa drama. Full control di tanganmu sendiri.

---

## 📋 Daftar Isi

1. [Tentang Proyek](#-tentang-proyek)
2. [Fitur Lengkap](#-fitur-lengkap)
3. [Screenshot & UI](#-screenshot--ui)
4. [Arsitektur Aplikasi](#-arsitektur-aplikasi)
5. [Struktur Project](#-struktur-project)
6. [Struktur Folder Anime](#-struktur-folder-anime)
7. [Teknologi yang Digunakan](#-teknologi-yang-digunakan)
8. [Dependencies Lengkap](#-dependencies-lengkap)
9. [Konfigurasi Gradle](#-konfigurasi-gradle)
10. [Penjelasan Setiap File](#-penjelasan-setiap-file)
11. [Data Layer](#-data-layer)
12. [UI Layer — Jetpack Compose](#-ui-layer--jetpack-compose)
13. [Player System](#-player-system)
14. [File Manager System](#-file-manager-system)
15. [Kategori System](#-kategori-system)
16. [Thumbnail System](#-thumbnail-system)
17. [Resume Playback System](#-resume-playback-system)
18. [Sleep Timer System](#-sleep-timer-system)
19. [OTG Flashdisk Support](#-otg-flashdisk-support)
20. [Permission System](#-permission-system)
21. [Cara Build & Install](#-cara-build--install)
22. [Cara Pakai Aplikasi](#-cara-pakai-aplikasi)
23. [Known Bugs & Limitasi](#-known-bugs--limitasi)
24. [Troubleshooting](#-troubleshooting)
25. [Proses Development](#-proses-development)
26. [Rencana Upgrade](#-rencana-upgrade)
27. [Catatan Developer](#-catatan-developer)
28. [Changelog](#-changelog)

---

## 🎯 Tentang Proyek

**AnimeFlix** adalah aplikasi Android untuk memutar video anime lokal dari penyimpanan HP atau flashdisk OTG, dengan tampilan yang terinspirasi dari Netflix. Proyek ini lahir dari kebutuhan pribadi untuk memiliki "Netflix versi lokal" — tempat menyimpan dan menonton koleksi anime tanpa bergantung pada koneksi internet atau layanan streaming berbayar.

### Latar Belakang

Sebelum AnimeFlix, koleksi anime disimpan menggunakan Aloha Browser yang memiliki fitur "hide media". Masalahnya:
- Ribet saat ingin pindah file
- Tidak ada tampilan yang bagus
- Tidak ada fitur streaming yang proper
- Tidak support flashdisk OTG dengan baik

AnimeFlix hadir sebagai solusi yang lebih elegant dan powerful.

### Filosofi Desain

- **Offline First** — Tidak ada koneksi internet yang dibutuhkan
- **Privacy First** — Video tidak muncul di galeri HP
- **User Friendly** — Tampilan modern seperti Netflix
- **Full Control** — Semua data ada di tangan pengguna

---

## ✨ Fitur Lengkap

### 🎭 Tampilan & UI
- Tampilan terinspirasi Netflix dengan tema hitam-merah
- Hero banner di halaman utama dari frame video atau poster custom
- Grid card per judul anime dengan thumbnail otomatis
- Section "Lanjutkan Nonton" untuk anime yang sedang ditonton
- Filter kategori: Anime, Movie, Series, Live Action
- Smooth animation dengan Jetpack Compose

### 📁 Manajemen File
- Buat folder anime baru dengan nama dan kategori
- Rename folder/file langsung dari aplikasi
- Hapus folder/episode
- Pindah file antar folder
- Long press untuk akses menu cepat

### 🎬 Player Video
- Fullscreen otomatis saat memutar video
- Custom player controls mirip Netflix
- Tombol Previous/Next episode
- Skip maju/mundur 10 detik (double tap)
- Progress bar merah dengan drag support
- Tampilan waktu current/total
- Info episode (EP 1 / 13)

### ⚡ Playback Features
- Playback speed: 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x
- Repeat episode (loop satu episode)
- Sleep timer: 5, 10, 15, 30, 60 menit
- Auto-next episode setelah selesai
- Resume dari posisi terakhir otomatis

### 🖼️ Thumbnail & Poster
- Thumbnail otomatis diambil dari frame video (detik ke-30)
- Support poster custom: JPG, JPEG, PNG, WEBP
- Cache thumbnail agar tidak load ulang
- Load thumbnail di background thread (tidak lag)

### 📱 Kategori System
- 4 kategori: Anime, Movie, Series, Live Action
- Kategori disimpan per folder
- Filter konten berdasarkan kategori

### 🔌 OTG Support
- Scan otomatis folder Anime di flashdisk OTG
- Putar video langsung dari flashdisk

### 🙈 Privacy
- Folder `.Anime` tersembunyi dari galeri HP
- Tidak ada cloud sync, semua lokal

### 📊 Progress Tracking
- Progress bar per episode menunjukkan % sudah ditonton
- "Lanjutkan Nonton" di halaman utama
- Simpan posisi terakhir per episode
- Riwayat 10 anime terakhir yang ditonton

---

## 📱 Screenshot & UI

### Halaman Utama (Home)
```
┌─────────────────────────────┐
│ ANIMEFLIX              [+]  │
├─────────────────────────────┤
│                             │
│    [Hero Banner - Anime]    │
│    Judul Anime              │
│    13 Episode · Anime       │
│    [▶ Putar] [ℹ Info]      │
│                             │
├─────────────────────────────┤
│ [Semua][Anime][Movie][Serie]│
├─────────────────────────────┤
│ Lanjutkan Nonton            │
│ [Card][Card][Card]→         │
├─────────────────────────────┤
│ Anime                       │
│ [Card][Card][Card]→         │
├─────────────────────────────┤
│ Movie                       │
│ [Card][Card][Card]→         │
└─────────────────────────────┘
```

### Halaman Detail
```
┌─────────────────────────────┐
│ ← [Banner Anime Full Width] │
│                             │
│    JUDUL ANIME              │
│    Anime · 13 Episode       │
│    [     ▶ Putar     ]      │
│    [✏ Rename] [🗑 Hapus]   │
├─────────────────────────────┤
│ Episodes                    │
├─────────────────────────────┤
│ [Thumb] EP 1                │
│         Judul Episode       │
│         24:02 · 124 MB    ⋮ │
├─────────────────────────────┤
│ [Thumb] EP 2                │
│         Judul Episode       │
│         24:02 · 139 MB    ⋮ │
└─────────────────────────────┘
```

### Player
```
┌─────────────────────────────┐
│ ← Judul Episode   🌙 🔁 ⚡ │
│                             │
│                             │
│  [⏮] [↺10] [ ⏸ ] [10↻] [⏭]│
│                             │
│                             │
│ EP 1 / 13                   │
│ ━━━━━●━━━━━━━━━━━━━━━━━━━━  │
│ 01:23                24:02  │
└─────────────────────────────┘
```

---

## 🏗️ Arsitektur Aplikasi

AnimeFlix menggunakan arsitektur MVVM-lite yang disederhanakan:

```
┌──────────────────────────────────────────┐
│                  UI Layer                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │HomeScreen│ │DetailScr │ │PlayerScr │ │
│  └──────────┘ └──────────┘ └──────────┘ │
│  ┌──────────────────────────────────────┐│
│  │      Screen sealed class navigation  ││
│  └──────────────────────────────────────┘│
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
│               Data Layer                  │
│  ┌────────────────────────────────────┐  │
│  │          AnimeRepository           │  │
│  │  - getAllAnime() suspend           │  │
│  │  - getEpisodes() suspend          │  │
│  │  - getThumbnail() suspend         │  │
│  │  - saveWatchedPosition()          │  │
│  └──────────────┬─────────────────────┘  │
└─────────────────┼────────────────────────┘
                  │
┌─────────────────▼────────────────────────┐
│             Storage Layer                 │
│  ┌──────────────┐  ┌────────────────┐   │
│  │ File System  │  │SharedPreferences│   │
│  │ /storage/    │  │ - positions    │   │
│  │ emulated/0/  │  │ - categories   │   │
│  │ .Anime/      │  │ - recent watch │   │
│  └──────────────┘  └────────────────┘   │
└──────────────────────────────────────────┘
```

### Flow Data

```
User tap anime card
       │
       ▼
HomeScreen.onAnimeClick(anime)
       │
       ▼
AnimeFlixApp → currentScreen = Screen.Detail(anime)
       │
       ▼
DetailScreen loads episodes via repository.getEpisodes()
       │
       ▼
User tap episode → onPlayEpisode(episode, episodes)
       │
       ▼
AnimeFlixApp → currentScreen = Screen.Player(episodes, index)
       │
       ▼
PlayerScreen initializes ExoPlayer, loads video
       │
       ▼
onDispose → saveWatchedPosition() → saveRecentlyWatched()
```

---

## 📂 Struktur Project

```
OfflineVideoPlayer/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/myempty/offlinevideoplayer/
│   │       │   ├── MainActivity.kt
│   │       │   ├── data/
│   │       │   │   ├── Models.kt
│   │       │   │   └── AnimeRepository.kt
│   │       │   └── ui/
│   │       │       ├── theme/
│   │       │       │   └── Theme.kt
│   │       │       └── screens/
│   │       │           ├── HomeScreen.kt
│   │       │           ├── DetailScreen.kt
│   │       │           ├── PlayerScreen.kt
│   │       │           └── AddAnimeDialog.kt
│   │       ├── assets/
│   │       │   └── fonts/
│   │       │       └── fa-solid.ttf
│   │       ├── res/
│   │       │   ├── values/
│   │       │   │   ├── strings.xml
│   │       │   │   └── themes.xml
│   │       │   └── mipmap-*/
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml
├── build.gradle
└── settings.gradle
```

---

## 📁 Struktur Folder Anime

```
/storage/emulated/0/
└── .Anime/                          ← Folder utama (tersembunyi dari galeri)
    ├── Plastic Memories/            ← Satu judul = satu folder
    │   ├── poster.jpg               ← (Opsional) Poster custom
    │   ├── Otakudesu_PlasMemo-01_720p.mp4
    │   ├── Otakudesu_PlasMemo-02_720p.mp4
    │   └── Otakudesu_PlasMemo-13_End_720p.mp4
    │
    ├── Your Name/                   ← Movie dalam satu folder
    │   ├── poster.png
    │   └── Your.Name.2016.1080p.mp4
    │
    ├── Attack on Titan/
    │   ├── S01E01.mkv
    │   ├── S01E02.mkv
    │   └── S04E28.mkv
    │
    └── Kimi no Na wa Live Action/
        └── episode.mp4
```

### Aturan Penamaan

| Hal | Aturan |
|-----|--------|
| Folder utama | Harus bernama `.Anime` (dengan titik di depan) |
| Subfolder | Nama bebas, ini yang jadi judul di app |
| File video | Nama bebas, urutan ditentukan alfabetis |
| Poster | Harus bernama `poster.jpg/png/webp` |
| Ekstensi video | `.mp4`, `.mkv`, `.avi`, `.mov`, `.webm` |

### Catatan Penting

- Satu folder = satu judul anime
- File video diurutkan berdasarkan nama file (alfabet)
- Untuk urutan yang benar, gunakan nomor di awal: `01_`, `02_`, dst.
- File non-video dalam folder akan diabaikan otomatis

---

## 🧱 Teknologi yang Digunakan

### Core
| Teknologi | Versi | Fungsi |
|-----------|-------|--------|
| Kotlin | 2.1.0 | Bahasa pemrograman |
| Android Gradle Plugin | 8.3.2 | Build system |
| Target SDK | 34 (Android 14) | Target platform |
| Min SDK | 24 (Android 7.0) | Versi minimum |

### UI Framework
| Library | Versi | Fungsi |
|---------|-------|--------|
| Jetpack Compose BOM | 2024.09.00 | UI framework modern |
| Compose Material3 | BOM | Material Design 3 |
| Compose Material Icons Extended | BOM | Icon library |
| Activity Compose | 1.9.0 | Bridge Activity ke Compose |
| Navigation Compose | 2.7.7 | Navigasi antar screen |

### Media
| Library | Versi | Fungsi |
|---------|-------|--------|
| Media3 ExoPlayer | 1.0.0 | Video player engine |
| Media3 UI | 1.0.0 | Player view component |
| MediaMetadataRetriever | Built-in | Ambil thumbnail & durasi |

### Image Loading
| Library | Versi | Fungsi |
|---------|-------|--------|
| Coil Compose | 2.5.0 | Load gambar poster async |

### Storage & Data
| Teknologi | Fungsi |
|-----------|--------|
| SharedPreferences | Simpan posisi, kategori, history |
| File API | Operasi folder dan file |

---

## 📦 Dependencies Lengkap

### `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.3.2"
kotlin = "2.1.0"
coreKtx = "1.9.0"
appcompat = "1.5.1"
material = "1.8.0"
constraintlayout = "2.1.4"
compose_bom = "2024.09.00"
activity_compose = "1.9.0"
navigation_compose = "2.7.7"
exoplayer = "1.0.0"
coil = "2.5.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose_bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity_compose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation_compose" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "exoplayer" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "exoplayer" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### `app/build.gradle`

```groovy
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace 'com.example.myempty.offlinevideoplayer'
    compileSdk 34

    defaultConfig {
        applicationId 'com.example.myempty.offlinevideoplayer'
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName '2.0'
    }

    buildFeatures {
        compose true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation libs.androidx.core.ktx
    implementation libs.androidx.appcompat
    implementation libs.material

    implementation platform(libs.compose.bom)
    implementation libs.compose.ui
    implementation libs.compose.ui.tooling.preview
    implementation libs.compose.material3
    implementation libs.compose.material.icons
    implementation libs.compose.runtime
    implementation libs.compose.foundation
    implementation libs.activity.compose
    implementation libs.navigation.compose
    debugImplementation libs.compose.ui.tooling

    implementation libs.media3.exoplayer
    implementation libs.media3.ui
    implementation libs.coil.compose
}
```

---

## ⚙️ Konfigurasi Gradle

### Kenapa AGP 8.3.2?

AGP 8.3.2 dipilih karena kompatibel dengan AndroidIDE di HP, mendukung Kotlin 2.1.0, mendukung Jetpack Compose dengan `kotlin.plugin.compose`, dan stabil untuk build di perangkat ARM.

### Kenapa Kotlin 2.1.0?

AndroidIDE menggunakan Kotlin 2.1.0 secara internal. Jika project menggunakan versi lebih rendah, akan muncul error:
```
Module was compiled with an incompatible version of Kotlin.
The binary version of its metadata is 2.1.0, expected version is 1.9.0.
```

### Kenapa tidak pakai `composeOptions.kotlinCompilerExtensionVersion`?

Di Kotlin 2.x, cara lama sudah deprecated. Sekarang menggunakan plugin terpisah:
```groovy
alias(libs.plugins.kotlin.compose)
```
Plugin ini otomatis menangani Compose compiler compatibility.

### Kenapa ExoPlayer 1.0.0 bukan versi terbaru?

Versi terbaru (1.3.x+) menyebabkan error AAPT2 di AndroidIDE. Versi 1.0.0 adalah yang paling stabil untuk environment AndroidIDE.

---

## 📄 Penjelasan Setiap File

### `MainActivity.kt`

Entry point aplikasi. Bertanggung jawab untuk:
- Setup fullscreen mode
- Inisialisasi `AnimeRepository`
- Request permission storage
- Set Compose content dengan `AnimeFlixTheme`
- Host `AnimeFlixApp` composable

**Sealed class `Screen`** untuk navigation state:
```kotlin
sealed class Screen {
    object Home : Screen()
    data class Detail(val anime: AnimeModel) : Screen()
    data class Player(val episodes: List<EpisodeModel>, val startIndex: Int) : Screen()
}
```

**`AnimeFlixApp` composable:**
- Menyimpan state `currentScreen`
- Render screen yang sesuai berdasarkan state
- Handle dialog tambah anime
- Handle `refreshKey` untuk reload data setelah operasi file

---

### `data/Models.kt`

Berisi semua data class yang digunakan di seluruh aplikasi.

**`AnimeCategory` enum:**
```kotlin
enum class AnimeCategory(val label: String) {
    ANIME("Anime"),
    MOVIE("Movie"),
    SERIES("Series"),
    LIVE_ACTION("Live Action"),
    ALL("Semua")
}
```

**`AnimeModel` data class:**
```kotlin
data class AnimeModel(
    val title: String,            // Nama folder
    val folderPath: String,       // Path absolut folder
    val episodeCount: Int,        // Jumlah file video
    val firstEpisodePath: String, // Path episode pertama
    val category: AnimeCategory,  // Kategori yang dipilih user
    val posterPath: String        // Path poster custom
)
```

**`EpisodeModel` data class:**
```kotlin
data class EpisodeModel(
    val title: String,      // Nama file tanpa ekstensi
    val path: String,       // Path absolut file video
    val duration: Long,     // Durasi dalam milidetik
    val size: Long,         // Ukuran file dalam bytes
    val episodeNumber: Int  // Nomor urut (1-based)
)
```

---

### `data/AnimeRepository.kt`

Single source of truth untuk semua operasi data. Semua operasi berat menggunakan `suspend fun` dengan `Dispatchers.IO`.

**Thumbnail Cache:**
```kotlin
private val thumbnailCache = mutableMapOf<String, Bitmap?>()
```

**`getAllAnime()`:**
1. Cek apakah folder `.Anime` ada, buat jika belum
2. List semua subfolder dan urutkan berdasarkan nama
3. Filter hanya folder yang punya file video
4. Buat `AnimeModel` untuk setiap folder valid

**`getEpisodes()`:**
1. List semua file di folder
2. Filter hanya file dengan ekstensi video
3. Urutkan berdasarkan nama file
4. Ambil durasi via `MediaMetadataRetriever`

**`getThumbnail()`:**
1. Cek cache, return dari cache jika ada
2. Jika tidak ada, jalankan di `Dispatchers.IO`
3. Decode frame pada waktu yang ditentukan
4. Simpan ke cache dan return

**Recently Watched System:**
Data disimpan sebagai string pipe-separated:
```
path1,timestamp1|path2,timestamp2|path3,timestamp3
```

**Kategori System:**
Disimpan di SharedPreferences dengan key `cat_[namaFolder]`.

---

### `ui/theme/Theme.kt`

```kotlin
val NetflixRed   = Color(0xFFE50914)
val NetflixBlack = Color(0xFF141414)
val NetflixDark  = Color(0xFF1F1F1F)
val NetflixGray  = Color(0xFF888888)
val NetflixWhite = Color(0xFFFFFFFF)
```

---

### `ui/screens/HomeScreen.kt`

**`HomeScreen`** — Root composable dengan:
- Loading indicator saat fetch data
- `LazyColumn` sebagai scroll container
- Section "Lanjutkan Nonton" (jika ada history)
- Section per kategori

**`HeroBanner`** — Banner besar:
- Load thumbnail di background
- Gradient overlay dari bawah
- Judul, info, tombol Putar & Info

**`CategoryTabs`** — Filter chip row:
- `FilterChip` Material3
- Merah jika terpilih

**`AnimeCard`** (120x180dp) dan **`AnimeCardWide`** (180x100dp):
- Load thumbnail async
- Gradient overlay
- Judul di pojok bawah

---

### `ui/screens/DetailScreen.kt`

**`DetailScreen`:**
- Load episodes dan banner di `LaunchedEffect`
- `LazyColumn` dengan banner + list episode
- Tombol Putar, Rename, Hapus

**`EpisodeItem`:**
- Thumbnail kecil (130x73dp)
- Progress bar merah di bawah thumbnail
- Badge durasi di pojok kanan bawah
- Info: EP number, judul, ukuran file
- Ikon ⋮ untuk menu

---

### `ui/screens/PlayerScreen.kt`

State yang dikelola:
```kotlin
var currentIndex by remember { mutableStateOf(startIndex) }
var showControls by remember { mutableStateOf(true) }
var isPlaying by remember { mutableStateOf(true) }
var currentPosition by remember { mutableStateOf(0L) }
var duration by remember { mutableStateOf(0L) }
var playbackSpeed by remember { mutableStateOf(1.0f) }
var isRepeat by remember { mutableStateOf(false) }
var sleepMinutesLeft by remember { mutableStateOf(0) }
```

**ExoPlayer lifecycle** via `DisposableEffect`:
- Setup listener saat composable enter
- Release player saat composable dispose

**Gesture detection:**
```kotlin
detectTapGestures(
    onTap = { showControls = !showControls },
    onDoubleTap = { offset ->
        if (offset.x < size.width / 2) seekBackward()
        else seekForward()
    }
)
```

**Auto-hide controls** via `LaunchedEffect(showControls)` dengan `delay(3000)`.

---

### `ui/screens/AddAnimeDialog.kt`

Dialog untuk menambah folder anime baru:
- `OutlinedTextField` untuk input nama
- `FilterChip` grid 2x2 untuk pilih kategori
- Validasi nama tidak boleh kosong

---

## 🗃️ Data Layer

### SharedPreferences Keys

| Key | Tipe | Fungsi |
|-----|------|--------|
| `cat_[folderName]` | String | Kategori per folder |
| `[videoPath]` | Long | Posisi terakhir per video (ms) |
| `recently_watched` | String | History nonton |

### Format `recently_watched`

```
/storage/.Anime/Anime1/ep1.mp4,1713000000000|/storage/.Anime/Anime2/ep3.mp4,1712900000000
```

### File System Operations

```kotlin
File(animeDir, name).mkdirs()           // Create folder
File(oldPath).renameTo(File(newPath))   // Rename
File(path).deleteRecursively()          // Delete folder
File(path).delete()                     // Delete file
File(src).renameTo(File(destFolder, fileName)) // Move
File(folderPath).listFiles()?.filter {} // List files
```

---

## 🎨 UI Layer — Jetpack Compose

### State Management

State hoisting sederhana — state di composable level tertinggi, diturunkan via parameter:

```
AnimeFlixApp (currentScreen, refreshKey)
    ├── HomeScreen (animeList, selectedCategory)
    │   ├── HeroBanner (bitmap)
    │   └── AnimeCard (bitmap)
    ├── DetailScreen (episodes, bannerBitmap)
    │   └── EpisodeItem (thumbnail, progress)
    └── PlayerScreen (currentIndex, isPlaying, position...)
```

### LaunchedEffect Usage

```kotlin
// Load data saat pertama render
LaunchedEffect(Unit) { animeList = repository.getAllAnime() }

// Load thumbnail saat path berubah
LaunchedEffect(anime.firstEpisodePath) {
    bitmap = repository.getThumbnail(anime.firstEpisodePath)
}

// Auto-hide controls
LaunchedEffect(showControls) {
    if (showControls) { delay(3000); showControls = false }
}
```

### Custom Navigation

Menggunakan sealed class, bukan `NavController`:

```kotlin
var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

when (val screen = currentScreen) {
    is Screen.Home -> HomeScreen(...)
    is Screen.Detail -> DetailScreen(anime = screen.anime, ...)
    is Screen.Player -> PlayerScreen(episodes = screen.episodes, ...)
}
```

**Alasan tidak pakai NavController:** Lebih simpel, tidak perlu serialisasi argument, state terjaga saat navigasi back.

---

## 🎬 Player System

### ExoPlayer Setup

```kotlin
val player = remember {
    ExoPlayer.Builder(context).build().apply {
        playWhenReady = true
    }
}
```

### Load Video

```kotlin
fun loadEpisode(index: Int) {
    val episode = episodes[index]
    repository.saveRecentlyWatched(episode.path)
    val saved = repository.getWatchedPosition(episode.path)
    player.clearMediaItems()
    player.setMediaItem(MediaItem.fromUri(Uri.parse(episode.path)))
    player.prepare()
    if (saved > 0L) player.seekTo(saved)
    player.playWhenReady = true
    player.setPlaybackSpeed(playbackSpeed)
}
```

### Player Events

```kotlin
object : Player.Listener {
    override fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
    }
    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            if (isRepeat) { player.seekTo(0); player.play() }
            else if (currentIndex < episodes.size - 1) currentIndex++
        }
    }
}
```

### Progress Tracking

```kotlin
LaunchedEffect(player) {
    while (true) {
        currentPosition = player.currentPosition
        duration = player.duration.takeIf { it > 0 } ?: 0L
        delay(500)
    }
}
```

### Playback Speed Options

- 0.5x — Slow motion
- 0.75x — Sedikit lambat
- 1.0x — Normal (default)
- 1.25x — Sedikit cepat
- 1.5x — Cepat
- 2.0x — Sangat cepat

---

## 🗂️ File Manager System

### Operasi yang Tersedia

**Level folder (HomeScreen):**
- ➕ Tambah folder baru
- ✏️ Rename folder
- 🗑️ Hapus folder beserta isinya

**Level episode (DetailScreen):**
- ✏️ Rename file
- 🗑️ Hapus file
- 📂 Pindah file ke folder lain

### Rename Folder

Setelah rename, kategori dipertahankan via `saveCategoryForFolder(newName, oldCat)`.

### Delete Folder

```kotlin
fun deleteFolder(path: String): Boolean = File(path).deleteRecursively()
```

**PERINGATAN: Operasi ini tidak bisa di-undo!**

### Move File

```kotlin
fun moveFile(path: String, targetFolderPath: String): Boolean {
    val src = File(path)
    return src.renameTo(File(targetFolderPath, src.name))
}
```

**Limitasi:** `renameTo()` hanya bekerja di filesystem yang sama (tidak bisa dari internal ke OTG).

---

## 🏷️ Kategori System

### Cara Kerja

1. User tambah folder → pilih kategori di dialog
2. Disimpan ke SharedPreferences: `cat_[nama folder]`
3. Saat load anime, kategori dibaca dari SharedPreferences
4. Default: `AnimeCategory.ANIME`

### Filter

```kotlin
val filteredList = if (selectedCategory == AnimeCategory.ALL) animeList
else animeList.filter { it.category == selectedCategory }
```

### Section per Kategori

```
Lanjutkan Nonton (jika ada)
Anime (jika ada)
Movie (jika ada)
Series (jika ada)
Live Action (jika ada)
```

Section kosong tidak ditampilkan.

---

## 🖼️ Thumbnail System

### Sumber Thumbnail (Prioritas)

1. **Poster custom** — Jika ada `poster.jpg/png/webp` di folder
2. **Frame video** — Diambil dari detik ke-30 episode pertama

### Proses Load

```
LaunchedEffect(path)
    │
    ├─ Cache hit → Return bitmap
    └─ Cache miss → Dispatchers.IO
                        │
                        ▼
              MediaMetadataRetriever
                        │
                        ▼
              getFrameAtTime(30s)
                        │
                        ▼
              Cache → Return → Recompose
```

### Waktu Frame

| Composable | Detik |
|------------|-------|
| AnimeCard | 30 detik |
| HeroBanner | 60 detik |
| EpisodeItem | 30 detik |

### Cache Policy

- In-memory `Map<String, Bitmap?>`
- Dihapus saat proses di-kill
- Tidak ada persistent cache ke disk
- Tidak ada batas ukuran (potential OOM untuk library besar)

---

## ▶️ Resume Playback System

Posisi disimpan saat:
- Video di-pause
- Player di-stop
- Composable di-dispose (app ke background/keluar)

```kotlin
// Save
repository.saveWatchedPosition(episodes[currentIndex].path, player.currentPosition)

// Restore
val saved = repository.getWatchedPosition(episode.path)
if (saved > 0L) player.seekTo(saved)
```

### Progress Bar

```kotlin
val progress = if (episode.duration > 0)
    (watchedPosition.toFloat() / episode.duration.toFloat()).coerceIn(0f, 1f)
else 0f
```

---

## 🌙 Sleep Timer System

```kotlin
LaunchedEffect(sleepMinutesLeft) {
    if (sleepMinutesLeft > 0) {
        delay(60000)
        if (sleepMinutesLeft == 1) {
            player.pause()
            sleepMinutesLeft = 0
        } else {
            sleepMinutesLeft--
        }
    }
}
```

Pattern ini menggunakan recomposition sebagai "timer tick".

**Opsi:** Off, 5, 10, 15, 30, 60 menit

Saat aktif, icon 🌙 berubah merah dengan menit tersisa di bawahnya.

---

## 🔌 OTG Flashdisk Support

```kotlin
// OTG storage
val storageDir = File("/storage")
storageDir.listFiles()?.forEach { storage ->
    if (storage.name != "emulated" && storage.canRead()) {
        val otgAnime = File(storage, ".Anime")
        if (otgAnime.exists()) scanDirs.add(otgAnime)
    }
}
```

### Persyaratan OTG

1. HP support OTG
2. Flashdisk terdeteksi di file manager sistem
3. Buat folder `.Anime` di root flashdisk
4. Taruh subfolder anime di dalam `.Anime/`

### Limitasi OTG

- Kecepatan tergantung USB speed dan kualitas flashdisk
- File di OTG tidak bisa di-rename/hapus via app
- Thumbnail loading lebih lambat

---

## 🔐 Permission System

```xml
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32"/>
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29"/>
```

### Flow Permission

```
App start
    │
    ├─ Android 11+ (API 30+)
    │      │
    │      └─ isExternalStorageManager()?
    │              ├─ YES → Load anime
    │              └─ NO → Open Settings untuk grant
    │
    └─ Android 10 ke bawah
           └─ Request READ_EXTERNAL_STORAGE
```

### Kenapa MANAGE_EXTERNAL_STORAGE?

Diperlukan untuk:
- Akses folder `.Anime` (tersembunyi)
- Operasi rename, delete, move file
- Akses folder yang tidak di-index MediaStore

---

## 🚀 Cara Build & Install

### Prerequisites

- AndroidIDE terinstall
- HP Android 7.0+ (API 24+)
- Storage minimal 2GB (untuk build cache)
- Koneksi internet untuk download dependencies

### Build via AndroidIDE

1. Buka project `OfflineVideoPlayer`
2. Tap tombol ▶️ di toolbar atas
3. Tunggu `BUILD SUCCESSFUL`

### Build via Terminal

```bash
cd /storage/emulated/0/AndroidIDEProjects/OfflineVideoPlayer
bash gradlew assembleDebug
```

### Copy & Install APK

```bash
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Download/AnimeFlix.apk
```

Buka File Manager → Download → AnimeFlix.apk → Install

### First Run

1. Buka AnimeFlix
2. Dialog izin "Kelola semua file" → Aktifkan
3. Tap `+` untuk buat folder anime
4. Salin file video ke folder yang dibuat

---

## 📖 Cara Pakai Aplikasi

### Tambah Anime Baru

1. Tap `+` di pojok kanan atas
2. Masukkan nama judul
3. Pilih kategori
4. Tap "Buat"
5. Salin file video ke folder yang terbuat di `.Anime/[nama]/`

### Tambah Poster Custom

1. Rename file gambar ke `poster.jpg`
2. Salin ke dalam folder anime
3. Restart aplikasi

### Pindah File

1. Buka detail anime
2. Tap ⋮ di sebelah episode
3. Pilih "Pindah"
4. Pilih folder tujuan

### Setup OTG

1. Colok flashdisk
2. Buat folder `.Anime` di root flashdisk
3. Buat subfolder per judul anime
4. Buka AnimeFlix

### Kontrol Player

| Aksi | Cara |
|------|------|
| Show/hide kontrol | Tap sekali |
| Skip -10 detik | Double tap kiri |
| Skip +10 detik | Double tap kanan |
| Play/Pause | Tombol tengah |
| Episode sebelumnya | Tombol ⏮ |
| Episode berikutnya | Tombol ⏭ |
| Kecepatan | Tombol ⚡ |
| Repeat | Tombol 🔁 |
| Sleep timer | Tombol 🌙 |
| Keluar | Tombol ← |
| Seek | Drag slider merah |

---

## 🐛 Known Bugs & Limitasi

### Bug yang Diketahui

#### 1. Thumbnail Cache Tidak Dibersihkan Setelah Rename
Setelah rename, cache lama tetap di memory. Fix planned:
```kotlin
thumbnailCache.remove(oldPath)
```

#### 2. Sleep Timer Tidak Akurat saat Background
Timer menggunakan coroutine delay yang bisa pause saat HP sleep. Fix planned: gunakan `AlarmManager`.

#### 3. "Lanjutkan Nonton" Tidak Update Real-time
Perlu `refreshKey++` manual setelah kembali dari player.

#### 4. OTG File Operations Gagal
`File.renameTo()` tidak bisa untuk file di OTG. Gunakan file manager sistem untuk kelola file OTG.

#### 5. Thumbnail Tidak Load untuk MKV Tertentu
Beberapa codec MKV tidak compatible dengan `MediaMetadataRetriever`. Solusi: tambah poster custom.

#### 6. App Crash saat Folder Kosong
Jika semua file video dihapus dari luar app, bisa crash saat load thumbnail. Fix planned: null check sebelum load.

#### 7. Seekbar Tidak Responsive saat Buffering
ExoPlayer masih buffering → seek tidak bekerja. Tunggu video load terlebih dahulu.

### Limitasi Teknis

| Limitasi | Detail |
|----------|--------|
| Thumbnail cache | Tidak terbatas — potensi OOM |
| Recently watched | Max 10 item |
| Format video | MP4, MKV, AVI, MOV, WEBM |
| Format poster | JPG, JPEG, PNG, WEBP |
| Min Android | 7.0 (API 24) |
| Subtitle | Tidak didukung |
| Streaming URL | Tidak didukung |
| Multiple user | Tidak didukung |
| Backup/restore | Tidak didukung |

---

## 🔧 Troubleshooting

### Aplikasi Crash Saat Dibuka

1. Pengaturan → Aplikasi → AnimeFlix → Izin → Aktifkan semua
2. Buat folder `.Anime` di storage manual
3. Hapus data aplikasi dan mulai ulang

### Video Tidak Muncul

1. Pastikan struktur: `.Anime/NamaAnime/video.mp4`
2. Gunakan ekstensi yang didukung
3. Grant MANAGE_EXTERNAL_STORAGE

### Thumbnail Tidak Muncul

1. Tambahkan poster custom (`poster.jpg`)
2. Coba play video — jika bisa berarti codec OK
3. Restart app untuk clear cache

### Player Tidak Putar

1. Coba format MP4 H.264
2. Re-download file
3. Hapus dan tambah ulang via app

### BUILD FAILED — AAPT2 Error

```bash
sed -i 's/agp = "8.x.x"/agp = "8.3.2"/' gradle/libs.versions.toml
sed -i 's/exoplayer = "1.x.x"/exoplayer = "1.0.0"/' gradle/libs.versions.toml
```

### BUILD FAILED — Kotlin Mismatch

```bash
sed -i 's/kotlin = "1.x.x"/kotlin = "2.1.0"/' gradle/libs.versions.toml
```

### BUILD FAILED — Compose Compiler

Pastikan menggunakan `kotlin.plugin.compose` (Kotlin 2.x approach):
```groovy
// Di build.gradle:
alias(libs.plugins.kotlin.compose)

// Hapus ini jika ada:
// composeOptions { kotlinCompilerExtensionVersion '...' }
```

### App Lambat / Lag

1. Pastikan `getThumbnail()` dipanggil via `LaunchedEffect`
2. Tambahkan poster custom untuk kurangi decode video
3. Tutup app lain di background

### Build Stuck saat Configure

Build Compose pertama kali memerlukan download ~500MB+. Pastikan:
- WiFi stabil
- Baterai cukup atau sambil charge
- Tunggu 15-30 menit

---

## 🏗️ Proses Development

### Timeline

| Fase | Deskripsi |
|------|-----------|
| V0.1 | Setup project AndroidIDE, build pertama berhasil |
| V0.2 | XML layout dasar, list video dari MediaStore |
| V0.3 | ExoPlayer integration, basic playback |
| V0.4 | Folder-based scanning, resume playback |
| V0.5 | Netflix-style UI dengan RecyclerView |
| V0.6 | Gesture controls, sleep timer, playback speed |
| V0.7 | File manager (rename, delete, move) |
| V1.0 | MANAGE_EXTERNAL_STORAGE, OTG support |
| V2.0 | Rework ke Jetpack Compose, kategori system |

### Tantangan yang Dihadapi

**1. AAPT2 Tidak Kompatibel**
AndroidIDE menggunakan AAPT2 versi ARM yang berbeda dengan yang di-download Gradle. Solusi: turunkan versi library.

**2. Kotlin Version Hell**
AndroidIDE memaksa Kotlin 2.1.0 internal, tapi Compose compiler lama support hingga 1.9.x. Solusi: gunakan `kotlin.plugin.compose`.

**3. Permission Storage Android 11+**
`MANAGE_EXTERNAL_STORAGE` memerlukan flow khusus — buka Settings page, tidak bisa request langsung.

**4. Thumbnail Lag**
Awalnya thumbnail di-load di main thread → UI freeze. Solusi: pindahkan ke `Dispatchers.IO`.

**5. File Manager via `File()` API**
Android 11+ membatasi `File()` API. Solusi: `MANAGE_EXTERNAL_STORAGE`.

**6. Jetpack Compose Build Pertama**
Download ~500MB+ dan kompilasi lama. Solusi: WiFi stabil, baterai cukup.

### Keputusan Teknis

**Kenapa tidak pakai ViewModel?**
RAM AndroidIDE terbatas. Untuk skala project ini, state di Composable sudah cukup.

**Kenapa tidak pakai Room Database?**
SharedPreferences cukup untuk data yang disimpan. Room menambahkan complexity yang tidak perlu.

**Kenapa sealed class untuk navigation?**
Navigation Compose memerlukan serialisasi argument yang kompleks untuk custom objects. Sealed class jauh lebih simpel dan type-safe.

---

## 🚀 Rencana Upgrade

### Prioritas Tinggi
- [ ] Search anime berdasarkan nama
- [ ] Sort & Filter (nama, tanggal, ukuran)
- [ ] Persistent thumbnail cache ke disk
- [ ] Fix OOM — batasi ukuran cache
- [ ] Batch operations (pilih banyak file)

### Prioritas Sedang
- [ ] Subtitle support (.srt, .ass)
- [ ] PiP Mode (Picture-in-Picture)
- [ ] Lock screen controls
- [ ] Custom poster via gallery picker
- [ ] Rating & catatan per anime

### Prioritas Rendah
- [ ] Multiple profiles
- [ ] Backup & restore settings
- [ ] Auto-skip intro/outro
- [ ] Volume/brightness gesture control
- [ ] Multiple audio track
- [ ] Playback queue
- [ ] Chromecast support

---

## 📝 Catatan Developer

### Tentang AndroidIDE

AndroidIDE adalah IDE Android yang berjalan langsung di HP. Keterbatasan:
- AAPT2 versi lebih lama (ARM binary)
- RAM terbatas → build lebih lambat
- Tidak ada layout preview
- Terminal tersedia untuk operasi manual

### Tips Development di AndroidIDE

1. Selalu build via tombol ▶️, bukan `./gradlew` di terminal
2. Jangan update versi library sembarangan
3. Buat file via terminal: `cat > file.kt << 'EOF'`
4. Cek error: `bash gradlew assembleDebug 2>&1 | grep "^e:"`
5. Jika stuck di configure, tunggu — Compose butuh download banyak

### Versi yang Terbukti Kompatibel

```toml
agp = "8.3.2"
kotlin = "2.1.0"
compose_bom = "2024.09.00"
exoplayer = "1.0.0"
coil = "2.5.0"
```

### Environment

- **IDE:** AndroidIDE (versi terbaru)
- **Device:** HP Android 15 (API 35)
- **Build Tool:** Gradle 9.0.0
- **Build Duration:** ~2-3 menit (cache warm), ~15-30 menit (first build Compose)
- **APK Size:** ~8-12 MB

---

## 📋 Changelog

### V2.0.0 (Current) — Compose Rework
- Rework penuh UI ke Jetpack Compose
- Netflix-inspired design dengan hero banner
- Kategori system (Anime, Movie, Series, Live Action)
- Section "Lanjutkan Nonton"
- Custom poster support
- Thumbnail load di background thread (no lag)
- Progress bar per episode
- Material3 design

### V1.0.0 — Full Featured XML Version
- MANAGE_EXTERNAL_STORAGE permission
- File manager lengkap (rename, delete, move)
- OTG flashdisk support
- Sleep timer, playback speed, repeat mode
- Grid/List toggle untuk episode
- Font Awesome icons

### V0.5.0 — Netflix UI Attempt
- RecyclerView grid layout
- Thumbnail dari frame video
- Dark theme Netflix colors
- Custom player controls

### V0.4.0 — Folder Based
- Scan berdasarkan folder
- Hidden dari galeri via `.Anime` folder
- Resume playback

### V0.3.0 — Player
- ExoPlayer integration
- Basic playback controls
- Landscape orientation

### V0.2.0 — Basic UI
- RecyclerView + VideoAdapter
- MediaStore scanning

### V0.1.0 — Initial Setup
- Project setup di AndroidIDE
- Build pertama berhasil

---

## 👤 Developer

**Satrio**  
Kelas 2026 — SMK Negeri 3 Tapung Hulu  
Proyek pribadi untuk koleksi anime offline

---

## 📜 Lisensi

Proyek ini dibuat untuk penggunaan pribadi.  
Bebas dimodifikasi dan dikembangkan — selama bukan untuk kepentingan komersial.

---

*README ini dibuat dengan ❤️ dan banyak debug session*  
*Total development time: beberapa hari penuh perjuangan dengan AAPT2*
