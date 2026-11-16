package com.flixbox.app.ui.screens

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flixbox.app.data.MediaRepository
import com.flixbox.app.ui.theme.*

// ── Settings ──────────────────────────────────────────────────────────────────
// Lean sesuai scope yang diminta: cuma berisi hal yang langsung berguna sekarang
// (bersihkan cache) + info dasar. Opsi lain (pilih root penyimpanan default, kualitas
// thumbnail, dll) bisa ditambah belakangan kalau memang dibutuhkan — belum ada di roadmap
// P0-P6 sehingga tidak dibuat berlebihan di awal.
@Composable
fun SettingsScreen(
    repository: MediaRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
        } catch (e: PackageManager.NameNotFoundException) { "-" }
    }

    Column(modifier = Modifier.fillMaxSize().background(FxBlack)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Kembali", tint = FxWhite)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Pengaturan", color = FxWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))
        SettingsSectionTitle("Penyimpanan")
        SettingsItem(
            icon = Icons.Default.CleaningServices,
            title = "Bersihkan Cache Thumbnail",
            subtitle = "Hapus thumbnail & durasi yang tersimpan di memory. Berguna kalau " +
                "ada thumbnail lama yang masih nyangkut setelah rename/hapus folder.",
            onClick = {
                repository.clearThumbnailCache()
                Toast.makeText(context, "Cache dibersihkan", Toast.LENGTH_SHORT).show()
            }
        )

        SettingsSectionTitle("Tentang")
        SettingsItem(
            icon = Icons.Default.Info,
            title = "FlixBox",
            subtitle = "Versi $versionName — perpustakaan video lokal offline-first",
            onClick = null
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title, color = FxGray, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { m -> if (onClick != null) m.clickable { onClick() } else m }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = FxRed, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = FxWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = FxGray, fontSize = 12.sp)
        }
    }
}
