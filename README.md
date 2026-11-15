# FlixBox

**FlixBox** adalah aplikasi Android untuk mengatur koleksi video lokal dan memutarnya dari satu tempat. Koleksi dapat dikelompokkan berdasarkan kategori, dilihat berdasarkan judul dan season, lalu diputar menggunakan pemutar video di dalam aplikasi.

FlixBox berfokus pada **file video milik pengguna di penyimpanan perangkat**. Aplikasi ini bukan layanan streaming dan tidak menyediakan katalog atau konten video dari server.

> **Status proyek:** masih dalam pengembangan. Struktur, tampilan, dan fitur dapat berubah.

## Fitur

### Koleksi media

- Mengelompokkan koleksi ke dalam kategori **Anime**, **Movie**, **Series**, dan **Live Action**.
- Menampilkan koleksi dalam halaman utama dengan banner pilihan, poster, dan thumbnail.
- Menampilkan bagian **Lanjutkan Nonton** berdasarkan riwayat pemutaran.
- Mencari koleksi berdasarkan judul.
- Menampilkan detail judul, jumlah episode, daftar episode, dan tab season jika koleksi memiliki folder season.
- Membuat folder judul, mengganti nama, dan menghapus koleksi.
- Menghapus file episode dari halaman detail.

### Impor dan pengelolaan file

- Menjelajahi penyimpanan internal dan penyimpanan eksternal yang terdeteksi perangkat.
- Memilih file video melalui file browser bawaan dan menyalinnya ke kategori serta folder judul di FlixBox.
- Membuat folder koleksi langsung dari aplikasi.
- Menggunakan gambar poster sendiri pada folder koleksi.

### Pemutar video

- Pemutaran video lokal menggunakan **AndroidX Media3 ExoPlayer**.
- Kontrol episode sebelumnya dan berikutnya, termasuk perpindahan otomatis setelah episode selesai.
- Lompat mundur atau maju 10 detik; ketuk dua kali sisi kiri atau kanan layar untuk melakukan seek.
- Kecepatan pemutaran: `0.5x`, `0.75x`, `1x`, `1.25x`, `1.5x`, dan `2x`.
- Mode ulang satu episode.
- Sleep timer: 5, 10, 15, 30, atau 60 menit.
- Geser vertikal di sisi kiri layar untuk mengatur kecerahan, dan di sisi kanan untuk mengatur volume.
- Pemutar beralih ke orientasi landscape dan menjaga layar tetap menyala selama pemutaran.
- Menyimpan posisi terakhir per episode agar pemutaran dapat dilanjutkan.

### Poster dan thumbnail

- Menggunakan gambar poster lokal jika tersedia.
- Membuat thumbnail dari frame video secara otomatis jika poster tidak tersedia.
- Menyimpan thumbnail dalam cache memori agar gambar yang sama tidak perlu dibuat ulang setiap kali ditampilkan.

## Struktur folder koleksi

FlixBox menggunakan folder utama bernama `.FlixBox` pada penyimpanan bersama perangkat. Di dalamnya, koleksi dipisahkan berdasarkan kategori.

Contoh struktur:

```text
/storage/emulated/0/.FlixBox/
├── Anime/
│   └── Judul Anime/
│       ├── poster.jpg
│       ├── Episode 01.mp4
│       └── Episode 02.mkv
├── Movie/
│   └── Judul Film/
│       ├── poster.webp
│       └── Film.mp4
├── Series/
│   └── Judul Series/
│       ├── Season 1/
│       │   ├── S01E01.mp4
│       │   └── S01E02.mp4
│       └── Season 2/
│           └── S02E01.mp4
└── Live Action/
    └── Judul Live Action/
        └── Episode 01.mp4
```

Nama folder setelah kategori akan digunakan sebagai judul koleksi. Folder di dalam folder judul dapat digunakan untuk memisahkan season. Untuk koleksi tanpa season, file video dapat diletakkan langsung di folder judul.

**Ekstensi video yang dikenali oleh pemindai koleksi:**

- `mp4`
- `mkv`
- `avi`
- `mov`
- `webm`

**Ekstensi gambar yang dikenali untuk poster:**

- `jpg` / `jpeg`
- `png`
- `webp`

Dukungan pemutaran akhir tetap bergantung pada codec dan kemampuan perangkat Android.

## Teknologi

| Komponen | Teknologi |
|---|---|
| Bahasa | Kotlin |
| UI | Jetpack Compose |
| Komponen UI | Material 3 |
| Pemutaran video | AndroidX Media3 ExoPlayer dan Media3 UI |
| Pemuatan gambar | Coil Compose |
| Penyimpanan preferensi | Android SharedPreferences |
| Metadata video dan thumbnail | `MediaMetadataRetriever` |
| Build system | Gradle dan Android Gradle Plugin |

## Struktur proyek

```text
FlixBox/
├── app/
│   ├── src/main/
│   │   ├── java/com/flixbox/app/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── MediaRepository.kt
│   │   │   │   └── Models.kt
│   │   │   └── ui/
│   │   │       ├── screens/
│   │   │       │   ├── HomeScreen.kt
│   │   │       │   ├── SearchScreen.kt
│   │   │       │   ├── AddMediaScreen.kt
│   │   │       │   ├── DetailScreen.kt
│   │   │       │   └── PlayerScreen.kt
│   │   │       └── theme/Theme.kt
│   │   └── res/
│   └── build.gradle
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle
└── settings.gradle
```

### Gambaran arsitektur

- **UI layer:** layar Compose untuk beranda, pencarian, impor, detail koleksi, dan pemutar.
- **Data layer:** `MediaRepository` menangani pemindaian folder, daftar episode, impor dan pengelolaan file, metadata video, thumbnail, serta riwayat tontonan.
- **Playback:** `PlayerScreen` menggunakan Media3 ExoPlayer untuk memutar episode dan mengelola kontrol pemutaran.
- **Preferensi lokal:** posisi tontonan dan daftar yang baru ditonton disimpan menggunakan `SharedPreferences`.

## Konfigurasi aplikasi

| Properti | Nilai pada konfigurasi proyek saat ini |
|---|---|
| Nama aplikasi | FlixBox |
| Application ID | `com.flixbox.app` |
| Version name | `1.0` |
| Version code | `1` |
| Minimum SDK | `24` (Android 7.0) |
| Compile SDK | `34` |
| Target SDK | `34` |
| Android Gradle Plugin | `8.3.2` |
| Kotlin | `2.1.0` |

## Izin penyimpanan

Pada konfigurasi saat ini, FlixBox meminta akses penyimpanan untuk memindai, membaca, mengimpor, dan mengelola file koleksi.

- Android 11 (API 30) dan lebih baru: aplikasi mengarahkan pengguna ke pengaturan khusus **All files access** (`MANAGE_EXTERNAL_STORAGE`).
- Android versi lebih lama: aplikasi meminta izin `READ_EXTERNAL_STORAGE`.

Berikan izin hanya jika kamu memahami kebutuhan akses aplikasi. Perubahan mekanisme akses file mungkin diperlukan sebelum distribusi melalui toko aplikasi.

## Build

Buka proyek melalui AndroidIDE atau Android Studio dengan Android SDK yang sesuai, lalu jalankan task Gradle yang diperlukan.

### Debug APK

```bash
./gradlew :app:assembleDebug
```

Hasil build:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

```bash
./gradlew :app:assembleRelease
```

### Android App Bundle (AAB)

```bash
./gradlew :app:bundleRelease
```

Hasil bundle:

```text
app/build/outputs/bundle/release/app-release.aab
```

APK adalah format yang dapat dipasang langsung pada perangkat Android. AAB adalah format publikasi: Google Play memproses AAB untuk menghasilkan APK yang sesuai dengan konfigurasi perangkat pengguna. AAB tidak dipasang langsung seperti APK.

> **Catatan release:** konfigurasi signing khusus untuk release belum didefinisikan di `app/build.gradle` pada basis kode ini. Siapkan upload key/release signing secara aman sebelum mendistribusikan build release. Jangan pernah commit keystore atau password signing ke repository publik.

## Catatan sebelum publikasi ke Google Play

Repository ini belum dapat dianggap siap publikasi ke Google Play hanya karena task AAB berhasil dibuat. Ada beberapa hal yang perlu ditinjau terlebih dahulu:

1. Proyek saat ini menggunakan `targetSdk 34`. Berdasarkan persyaratan Google Play yang berlaku mulai **31 Agustus 2026**, aplikasi Android baru dan update aplikasi harus menargetkan **Android 16 / API level 36 atau lebih tinggi**.
2. Manifest saat ini mendeklarasikan `MANAGE_EXTERNAL_STORAGE`. Google Play membatasi izin All files access dan mewajibkan deklarasi serta peninjauan. Kebijakan juga menyebut akses file media dan pemilihan file individual sebagai penggunaan yang tidak memenuhi syarat untuk izin ini. Evaluasi penggunaan `MediaStore` atau Storage Access Framework (SAF), atau pastikan kasus penggunaan manajemen file benar-benar memenuhi kebijakan sebelum mengajukan aplikasi.
3. Siapkan release signing, privacy policy, deklarasi izin, dan pengujian pada berbagai perangkat sebelum publikasi.

Dokumentasi resmi:

- [Android App Bundle](https://developer.android.com/guide/app-bundle)
- [Persyaratan target API Google Play](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Kebijakan All files access](https://support.google.com/googleplay/android-developer/answer/10467955)

## Batasan yang diketahui

- FlixBox saat ini berfokus pada koleksi dan file video lokal; fitur streaming dari internet belum tersedia.
- Pemindaian koleksi mengikuti struktur folder kategori yang dijelaskan di atas.
- Saat mengimpor sebuah folder, implementasi saat ini menyalin file yang berada langsung di dalam folder tersebut; struktur subfolder di dalam sumber tidak disalin secara rekursif.
- Format file yang terdeteksi belum menjamin seluruh kombinasi container dan codec dapat diputar di setiap perangkat.
- Proyek masih dikembangkan dan belum memiliki rilis publik yang ditandai.

## Lisensi

Lisensi proyek belum ditentukan. Jika repository akan dipublikasikan sebagai open source, tambahkan file `LICENSE` dan pilih lisensi yang sesuai sebelum mengizinkan penggunaan ulang kode.
