# Changelog — FlixBox

Semua perubahan penting dicatat di sini. Format bebas, urut dari versi terbaru.

---

## v2.2.0 — Continue Watching lanjut otomatis (P2, sebagian) & perbaikan thumbnail lag

- **Lanjutkan Nonton** di Home sekarang langsung buka Player di episode & posisi
  terakhir (sebelumnya cuma buka halaman Detail lagi, harus cari manual episodenya)
- Posisi tontonan disimpan **berkala tiap 10 detik** selama main — sebelumnya cuma
  disimpan saat pindah episode/keluar player, jadi progres bisa mundur jauh kalau app
  ke-kill paksa
- P2 masih sebagian: "status selesai ditonton" (badge/tanda episode yang sudah
  tuntas) belum dikerjakan
- **Perbaikan thumbnail "muncul tapi lama/lag":**
  - Tambah **disk cache** untuk thumbnail (`cacheDir/thumbs`) — sebelumnya cuma ada
    cache di memory, jadi tiap buka app dari kondisi dingin semua thumbnail didekode
    ulang dari video dari nol. Sekarang cuma didekode sekali seumur file.
  - Perbaiki bug: dua tempat di UI yang minta thumbnail sama persis di saat
    bersamaan, salah satunya dulu langsung dikasih kosong permanen walau yang lain
    berhasil — sekarang keduanya nunggu hasil yang sama
  - Tombol "Bersihkan Cache Thumbnail" di Pengaturan sekarang ikut membersihkan
    disk cache, bukan cuma memory

## v2.1.0 — Rename, Import System rekursif & Optimasi Performa

- Rename proyek dari AnimeFlix → **FlixBox**, package `com.flixbox.app`
- `MediaRepository.importToFlixBox()` dirombak (P1): rekursif (struktur season ikut
  tersalin), penanganan konflik file (skip/rename otomatis, tidak pernah menimpa),
  progress akurat lintas item, dan pembatalan yang benar-benar berfungsi
- **Optimasi performa:**
  - Thumbnail didekode di ukuran kecil (480×270), bukan resolusi asli video — cache
    yang sebelumnya cuma muat ~4 thumbnail resolusi penuh sebelum ke-evict, sekarang
    muat puluhan, jadi tidak decode ulang tiap scroll
  - `getAllMedia()` konsolidasi jadi 1x scan per folder judul (sebelumnya 4+ scan
    terpisah per judul)
  - Hapus pemanggilan `getAllMedia()` yang dobel (Home sebelumnya scan sendiri,
    padahal sudah ada hasil yang sama dari `MainActivity`)
- Halaman **Pengaturan** baru: bersihkan cache thumbnail manual, info versi app
- Dokumentasi (README) diperbarui agar sesuai working copy saat ini

## V2.0.0 — Compose Rework (era AnimeFlix)

- Rework penuh UI ke Jetpack Compose, desain gaya Netflix dengan hero banner
- Sistem kategori, section "Lanjutkan Nonton", poster custom, Material3

## V1.0.0 — Full Featured (XML version)

- `MANAGE_EXTERNAL_STORAGE`, file manager (rename/delete/move), OTG, sleep timer,
  playback speed, repeat mode

## V0.1–V0.5 — Setup awal hingga Netflix UI pertama

- Setup AndroidIDE, MediaStore scanning, integrasi ExoPlayer, folder-based scanning,
  resume playback, UI Netflix pertama dengan RecyclerView
