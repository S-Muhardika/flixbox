# Changelog — FlixBox

Semua perubahan penting dicatat di sini. Format bebas, urut dari versi terbaru.

---

## v2.5.0 — P4: Favorite, Watchlist, status tontonan, pencarian episode, sorting, metadata

- **Favorite** & **Watchlist** — tombol toggle di Detail, section baru di Home
  (muncul kalau ada isinya)
- **Filter status tontonan** — dialog "Filter & Urutkan" baru (ikon funnel di
  sebelah kategori di Home): Semua / Belum Ditonton / Sedang Ditonton / Selesai.
  Dihitung dari counter ringan, bukan scan ulang semua episode, jadi filter tetap
  cepat walau koleksi besar
- **Sorting** — di dialog yang sama: A-Z, Z-A, Terbaru Ditambah
- **Pencarian episode** — SearchScreen sekarang juga cari nama episode di semua
  judul (di-debounce 400ms, scan nama file doang — bukan decode thumbnail/durasi),
  tap hasilnya langsung buka Player di episode itu
- **Pengelolaan metadata** — "Rename" di Detail jadi "Edit Info": ubah judul DAN
  kategori sekaligus (pindah folder ke kategori lain otomatis)
- **Fix sekalian ditemukan saat kerjain ini:** rename/pindah kategori lewat Edit
  Info sekarang otomatis memindahkan posisi tontonan, status selesai ditonton,
  Favorite, Watchlist, dan Lanjutkan Nonton ke path barunya. Sebelum fix ini,
  semuanya akan ter-reset tiap kali judul di-edit (karena semua tersimpan per-path)
- Catatan: migrasi di atas cuma jalan kalau diubah **lewat Edit Info di app ini** —
  kalau folder judul di-rename/dipindah lewat file manager lain di luar app,
  status-status tersebut tetap akan reset

## v2.4.0 — P3: Subtitle eksternal & pemilihan audio track

- **Subtitle eksternal** (`.srt`, `.vtt`, `.ass`/`.ssa` — ketiganya native Media3,
  tanpa dependency baru):
  - Auto-terdeteksi kalau nama file subtitle sama dengan video (beda ekstensi,
    boleh ada tambahan kode bahasa, mis. `Episode 01.id.srt`)
  - Kalau nama tidak cocok: tombol "+ Tambah Subtitle Manual" di dialog Subtitle &
    Audio, pilih file lewat file browser — sekali pilih, otomatis kedetect lagi
    lain kali (file disalin & disesuaikan namanya)
  - Sinkronisasi: geser ±0.5 detik kalau subtitle tidak pas dengan videonya,
    tersimpan per file subtitle
  - ASS/SSA cuma didukung "minimal" oleh Media3 — teks & timing benar, styling
    custom ala fansub (posisi, animasi, warna khusus) kemungkinan tidak ter-render
- **Pemilihan audio track** — kalau file videonya punya lebih dari satu track audio
  (dual audio dkk), bisa pilih lewat dialog yang sama
- Tombol baru di player (ikon subtitle) untuk buka dialog "Subtitle & Audio"
- *Tidak termasuk di P3 ini: terjemahan otomatis subtitle (item terpisah, lihat
  diskusi sebelumnya) dan pengaturan posisi subtitle di layar*
- Catatan kecil: ganti/geser subtitle pada file subtitle yang sangat besar bisa
  terasa nge-lag sepersekian detik (operasi baca-tulis file teks, bukan video,
  jadi dampaknya kecil untuk subtitle berukuran wajar)

## v2.3.0 — P2 selesai: status selesai ditonton

- Episode yang sudah ditonton sampai 15 detik terakhir (toleransi credit/outro)
  ditandai selesai ditonton secara permanen (tetap nempel walau di-rewind lagi)
- Badge centang merah muncul di pojok thumbnail episode yang sudah selesai ditonton
  (halaman Detail)
- **P2 sekarang selesai 100%**: lanjut otomatis ke episode terakhir ✓, simpan
  progres berkala ✓, status selesai ditonton ✓

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
