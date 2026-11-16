# Changelog — FlixBox

Semua perubahan penting dicatat di sini. Format bebas, urut dari versi terbaru.

---

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
