package com.xyoruz.tokokita

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokoScreen(vm: StoreVM) {
    val ctx = LocalContext.current
    val info = vm.info
    var name by remember(info) { mutableStateOf(info.name) }
    var address by remember(info) { mutableStateOf(info.address) }
    var phone by remember(info) { mutableStateOf(info.phone) }
    var logo by remember(info) { mutableStateOf<Uri?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var nota by remember(vm.nota) { mutableStateOf(vm.nota) }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) logo = it }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        if (u != null) vm.backup(u) { ok -> toast(if (ok) "Backup berhasil" else "Backup gagal") }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) restoreUri = u
    }
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Toko")
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(shape = MaterialTheme.shapes.large, colors = cardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pengaturan Toko", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center) {
                        val model: Any? = logo ?: info.logoPath?.let { File(it) }
                        if (model != null) AsyncImage(model = model, contentDescription = "Logo",
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text("Logo", color = Muted)
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Nama toko") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text("Alamat (tampil di struk)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("No. telepon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            vm.saveInfo(StoreInfo(name.trim().ifBlank { "TokoKita" }, address.trim(), phone.trim(), info.logoPath), logo)
                            toast("Pengaturan disimpan")
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("Simpan", fontWeight = FontWeight.Bold) }
                }
            }
            Card(shape = MaterialTheme.shapes.large, colors = cardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tampilan Nota", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("Tampilkan logo", nota.showLogo) { nota = nota.copy(showLogo = it) }
                    SwitchRow("Tampilkan alamat", nota.showAddress) { nota = nota.copy(showAddress = it) }
                    SwitchRow("Tampilkan telepon", nota.showPhone) { nota = nota.copy(showPhone = it) }
                    OutlinedTextField(nota.header, { nota = nota.copy(header = it) }, label = { Text("Teks atas (opsional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nota.footer, { nota = nota.copy(footer = it) }, label = { Text("Teks bawah (mis. terima kasih)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Lebar kertas", Modifier.weight(1f))
                        FilterChip(selected = nota.paper == 58, onClick = { nota = nota.copy(paper = 58) }, label = { Text("58 mm") })
                        FilterChip(selected = nota.paper == 80, onClick = { nota = nota.copy(paper = 80) }, label = { Text("80 mm") })
                    }
                    Text("Pratinjau", color = Muted)
                    val sample = remember { previewSale() }
                    Box(Modifier.fillMaxWidth(), Alignment.TopCenter) {
                        ReceiptView(StoreInfo(name.trim().ifBlank { "TokoKita" }, address.trim(), phone.trim(), info.logoPath), nota, sample)
                    }
                    Button(
                        onClick = { vm.saveNota(nota); toast("Tampilan nota disimpan") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("Simpan tampilan nota", fontWeight = FontWeight.Bold) }
                }
            }
            Card(shape = MaterialTheme.shapes.large, colors = cardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Backup menyimpan semua produk, foto, transaksi, dan pengaturan toko ke satu file .zip.", color = Muted)
                    Button(
                        onClick = {
                            val d = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                            backupSaver.launch("tokokita-backup-$d.zip")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("Backup data") }
                    OutlinedButton({ restorePicker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Restore data") }
                }
            }
        }
    }
    restoreUri?.let { u ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore data?") },
            text = { Text("Semua produk dan transaksi saat ini akan diganti dengan isi file backup.") },
            confirmButton = {
                TextButton({
                    vm.restore(u) { ok -> toast(if (ok) "Restore berhasil" else "Restore gagal: file tidak valid") }
                    restoreUri = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton({ restoreUri = null }) { Text("Batal") } }
        )
    }
}
