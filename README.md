# 🎬 FlixBox — Perpustakaan Video Lokal Offline-First

> Aplikasi Android untuk mengelola & memutar koleksi video lokal, dengan pengalaman
> pengguna terinspirasi Netflix. 100% offline — tidak ada backend, login, cloud sync,
> komunitas, atau AI di dalam app itu sendiri.

**Versi saat ini:** `2.2.0` (lihat [Changelog](#-changelog))

---

## 📋 Daftar Isi

1. [Tentang Proyek](#-tentang-proyek)
2. [Fitur](#-fitur)
3. [Struktur Folder Data](#-struktur-folder-data)
4. [Arsitektur](#-arsitektur)
5. [Tech Stack](#-tech-stack)
6. [Permission](#-permission)
7. [Lingkungan Development](#-lingkungan-development)
8. [Build & Install](#-build--install)
9. [Known Limitations](#-known-limitations)
10. [Roadmap](#-roadmap)
11. [Changelog](#-changelog)
12. [Lisensi & Developer](#-lisensi--developer)

---

## 🎯 Tentang Proyek

FlixBox adalah perpustakaan media pribadi untuk koleksi video yang sudah ada di
penyimpanan HP (internal maupun eksternal) — bukan aplikasi streaming online. Fokus
utamanya: mengelompokkan media berdasarkan judul/kategori/season/episode, memutar
dengan player yang nyaman, dan mengingat posisi tontonan.

**Filosofi:**
- **Offline first** — tidak butuh koneksi internet
- **Privacy** — koleksi disimpan di folder tersembunyi (`.FlixBox`), tidak muncul di galeri
- **Full control** — semua data di tangan pengguna, tidak ada cloud

---

## ✨ Fitur

### Tampilan
- Home dengan hero banner (auto-rotate tiap ~6 detik dari daftar media)
- Filter kategori: **Anime, Movie, Series, Live Action**
- Section "Lanjutkan Nonton" untuk media yang sedang ditonton
- Tema gelap gaya Netflix (hitam-merah), Jetpack Compose + Material3
- Halaman **Pengaturan** — bersihkan cache thumbnail manual, info versi app

### Manajemen Media
- File browser bawaan untuk import dari penyimpanan internal/eksternal
- **Import rekursif** — folder dengan subfolder season ikut tersalin apa adanya,
  konflik nama file ditangani (skip kalau identik, disalin dengan nama baru kalau beda),
  progress akurat lintas banyak item sekaligus, dan bisa dibatalkan di tengah proses
- Buat folder judul baru + pilih kategori
- Rename & hapus folder judul, hapus episode satuan
- Grouping otomatis per season (folder = satu season) atau flat (tanpa season)
- Poster custom opsional — taruh file gambar (jpg/jpeg/png/webp) apa saja di folder
  judul/season, otomatis dipakai sebagai poster

### Player (Media3/ExoPlayer)
- Fullscreen landscape otomatis saat memutar
- Playback speed: 0.5x–2x, repeat episode, sleep timer
- Previous/next episode + auto-next setelah episode selesai
- Double-tap seek ±10 detik, gesture volume (kanan) & brightness (kiri)
- Resume otomatis dari posisi terakhir per episode; posisi disimpan berkala (tiap
  10 detik) selama main, bukan cuma saat pindah episode/keluar player
- Progress bar merah per episode di daftar episode
- Tap "Lanjutkan Nonton" di Home langsung buka Player di episode & posisi terakhir
  (tidak perlu buka Detail dulu)

### Lainnya
- Search berdasarkan judul media
- Thumbnail diambil otomatis dari frame video (via `MediaMetadataRetriever`) di
  ukuran kecil (480×270), di-cache dua lapis: memory (`LruCache`) dan disk
  (`cacheDir/thumbs`) — sekali didekode, tidak perlu decode ulang tiap buka app
- Mendukung penyimpanan internal **dan** eksternal secara otomatis

---

## 📁 Struktur Folder Data

```
/storage/emulated/0/
└── .FlixBox/                          ← folder utama, tersembunyi dari galeri
    ├── Anime/
    │   ├── Attack on Titan/
    │   │   ├── poster.jpg             ← opsional, nama file bebas (ekstensi gambar)
    │   │   ├── Season 1/
    │   │   │   ├── S01E01.mkv
    │   │   │   └── S01E02.mkv
    │   │   └── Season 2/
    │   │       └── S02E01.mkv
    │   └── Plastic Memories/          ← tanpa subfolder = flat, episode langsung di root judul
    │       ├── 01.mp4
    │       └── 02.mp4
    ├── Movie/
    │   └── Your Name/
    │       └── Your.Name.2016.mp4
    ├── Series/
    └── Live Action/
```

**Aturan:**
| Hal | Aturan |
|-----|--------|
| Folder root | `.FlixBox` (dibuat otomatis di penyimpanan internal) |
| Subfolder kategori | `Anime`, `Movie`, `Series`, `Live Action` — dibuat otomatis |
| Folder judul | Satu folder = satu judul; nama folder = judul yang tampil di app |
| Season | Opsional — subfolder apa pun di dalam folder judul dianggap satu season |
| Urutan episode | Dicoba deteksi dari pola `E01`, `S01E01`, atau angka yang dipisah `-`/`.`/spasi di nama file; kalau tidak ketemu, urut alfabetis |
| Urutan season | Dicoba deteksi dari pola `Season 1` / `S1`; kalau tidak ketemu, urut alfabetis di akhir |
| Ekstensi video | `.mp4`, `.mkv`, `.avi`, `.mov`, `.webm` |
| Ekstensi poster | `.jpg`, `.jpeg`, `.png`, `.webp` |

**Penyimpanan eksternal (kartu SD / OTG):** FlixBox mendeteksi otomatis lewat
`getExternalFilesDirs()`, tapi **hanya kalau folder `.FlixBox` sudah ada lebih dulu**
di root penyimpanan itu — tidak dibuat otomatis seperti di penyimpanan internal.

---

## 🏗️ Arsitektur

Compose + state hoisting sederhana, navigasi pakai `sealed class Screen` manual
(bukan Navigation Compose) — lebih simpel untuk passing object kompleks dan state
tetap terjaga saat back.

```
MainActivity (permission + setContent)
  └── FlixBoxApp (currentScreen, refreshKey, BackHandler global)
        ├── HomeScreen     → hero banner, kategori, Lanjutkan Nonton
        ├── SearchScreen   → cari berdasarkan judul
        ├── DetailScreen   → season tabs, daftar episode, rename/hapus
        ├── PlayerScreen   → ExoPlayer, kontrol, gesture
        ├── AddMediaScreen → buat folder / file browser & import
        └── SettingsScreen → bersihkan cache thumbnail, info versi

data/MediaRepository  → single source of truth, semua I/O berat via Dispatchers.IO.
                         getAllMedia() di-scan sekali per sesi Home (hasil dibagi ke
                         HomeScreen & SearchScreen, tidak di-scan dobel lagi)
data/Models           → MediaCategory, MediaModel, SeasonModel, EpisodeModel
SharedPreferences     → posisi tontonan per episode + riwayat 20 terakhir ditonton
```

Tidak pakai ViewModel maupun Room — state cukup di level Composable, dan semua data
dibaca langsung dari filesystem (dievaluasi ulang di P6 kalau koleksi sudah besar).

---

## 🧱 Tech Stack

| Komponen | Versi |
|---|---|
| Kotlin | 2.1.0 |
| Android Gradle Plugin | 8.3.2 |
| compileSdk / targetSdk | 34 (Android 14) |
| minSdk | 24 (Android 7.0) |
| Jetpack Compose BOM | 2024.09.00 |
| Media3 ExoPlayer/UI | 1.0.0 |
| Coil Compose | 2.5.0 |

**Versi-versi di atas dikunci dengan sengaja** karena kompatibel dengan AndroidIDE di
HP (ARM64). Versi Media3/AGP/Kotlin yang lebih baru pernah menyebabkan error AAPT2 /
Kotlin compiler mismatch di lingkungan ini — jangan upgrade tanpa alasan teknis jelas
dan tanpa test build penuh di AndroidIDE dulu.

---

## 🔐 Permission

```xml
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29"/>
<uses-permission android:name="android.permission.WRITE_SETTINGS"/>
```

- **Android 11+ (API 30+):** minta `MANAGE_EXTERNAL_STORAGE` lewat halaman Settings
  khusus (tidak bisa direct request) — dicek ulang tiap `onResume()`.
- **Android ≤10:** minta `READ_EXTERNAL_STORAGE` lewat dialog permission biasa.
- `MANAGE_EXTERNAL_STORAGE` diperlukan untuk akses folder tersembunyi `.FlixBox` dan
  operasi rename/delete/import yang tidak ter-index MediaStore.

---

## 💻 Lingkungan Development

- **Lokasi proyek:** `/storage/emulated/0/AndroidIDEProjects/flixbox`
- **AndroidIDE** = lingkungan build utama (tombol ▶️ di toolbar). Jangan build lewat
  `./gradlew` di Termux — native tooling ARM64 Termux berbeda dari yang dipakai AndroidIDE.
- **Termux** = untuk menjalankan perintah, memeriksa file, dan mengubah kode (mis.
  `cat > file.kt << 'EOF' ... EOF`).
- Konfigurasi Gradle/JDK/SDK kedua lingkungan **tidak disamakan** — treat sebagai dua
  environment terpisah.

---

## 🚀 Build & Install

```bash
cd /storage/emulated/0/AndroidIDEProjects/flixbox
```

1. Buka project di AndroidIDE, tap ▶️, tunggu `BUILD SUCCESSFUL`.
2. APK debug ada di `app/build/outputs/apk/debug/app-debug.apk`.
3. Salin & install manual lewat File Manager kalau tidak auto-install.
4. `bundleRelease` (AAB) saat ini **gagal** di AndroidIDE karena kendala Kotlin
   compile daemon & native Jansi — bukan prioritas. Fokus distribusi: **APK Debug**
   untuk pengujian, **APK Release** setelah konfigurasi signing diverifikasi.

---

## 🐛 Known Limitations

- Rename/hapus folder tidak membersihkan entry lama di cache thumbnail (memory
  **dan** disk) atau cache durasi secara otomatis — thumbnail basi bisa nongol
  sampai tergusur LRU/app restart. Workaround manual: Pengaturan → Bersihkan Cache
  Thumbnail.
- Posisi tontonan disimpan tiap 10 detik selama main (bukan tiap detik) — kalau app
  ke-kill paksa di antara dua penyimpanan, maksimal ~10 detik progres yang hilang.
- Belum ada tanda/badge "sudah selesai ditonton" per episode (sisa dari P2).
- Release build (AAB) gagal di AndroidIDE (lihat bagian Build di atas).
- Belum ada pemilihan subtitle/audio track (direncanakan P3).
- Belum ada Picture-in-Picture, MediaSession, notifikasi player, atau background
  playback (direncanakan P5).
- Belum ada database (Room) atau SAF — semua media di-scan langsung dari filesystem
  tiap buka Home; berpotensi mulai lambat untuk koleksi sangat besar (dievaluasi P6).
- Thumbnail cache hanya di memory, hilang tiap app di-kill — belum ada cache
  persistent ke disk (direncanakan P6).
- Import satu file berukuran sangat besar tidak bisa dibatalkan di tengah proses
  penyalinan file itu sendiri — pembatalan berlaku di antar-file.

---

## 🗺️ Roadmap

- [x] **P0** — Audit source terbaru, stabilisasi, pengujian dasar
- [x] **P1** — Import System rekursif: pertahankan struktur folder season, tangani
      konflik file, progres import akurat, dan pembatalan
- [~] **P2** (sebagian) — ~~Continue Watching langsung lanjut ke episode terakhir~~,
      ~~simpan progres berkala~~ sudah; status selesai ditonton per episode belum
- [ ] **P3** — Pemilihan subtitle & audio track, termasuk subtitle eksternal
- [ ] **P4** — Favorite, Watchlist, status tontonan, pencarian episode, sorting,
      pengelolaan metadata
- [ ] **P5** — Picture-in-Picture, MediaSession, notifikasi player, background playback
- [ ] **P6** — Evaluasi Room, SAF, MediaStore, OTG, persistent thumbnail cache,
      optimasi performa

---

## 📝 Changelog

Riwayat perubahan lengkap ada di [`CHANGELOG.md`](CHANGELOG.md) (file terpisah, satu
folder sama dengan README ini). Versi terbaru:

**v2.1.0** — Rename AnimeFlix → FlixBox, Import System rekursif (P1), optimasi
performa (thumbnail kecil + scan folder dikonsolidasi + hapus scan dobel), dan
halaman Pengaturan baru.

---

## 📜 Lisensi & Developer

**Developer:** Muhardika — SMK Negeri 3 Tapung Hulu, Riau

Proyek pribadi untuk koleksi video offline. Bebas dimodifikasi dan dikembangkan,
selama bukan untuk kepentingan komersial.
