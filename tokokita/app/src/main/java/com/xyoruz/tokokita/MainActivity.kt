package com.xyoruz.tokokita

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Gold = Color(0xFFC9A227)
val Muted = Color(0xFF9AA0A6)

fun rp(n: Long) = "Rp " + String.format(Locale.US, "%,d", n).replace(',', '.')

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TokoTheme { App() } }
    }
}

@Composable
fun TokoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Color.Black,
            background = Color(0xFF0F1115), onBackground = Color(0xFFEDEDED),
            surface = Color(0xFF181B21), onSurface = Color(0xFFEDEDED),
            surfaceVariant = Color(0xFF22262E), onSurfaceVariant = Muted,
            secondaryContainer = Color(0xFF3A3220), onSecondaryContainer = Gold,
            error = Color(0xFFE57373)
        ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)),
        content = content
    )
}

@Composable
fun App(vm: StoreVM = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val items = listOf(
        "Beranda" to Icons.Filled.Home, "Produk" to Icons.Filled.Menu,
        "Kasir" to Icons.Filled.ShoppingCart, "Laporan" to Icons.Filled.List,
        "Toko" to Icons.Filled.Settings
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (l, ic) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(ic, l) }, label = { Text(l) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> Dashboard(vm); 1 -> ProductScreen(vm); 2 -> Kasir(vm); 3 -> ReportScreen(vm); else -> TokoScreen(vm) }
        }
    }
}

@Composable
fun Header(t: String) = Text(
    t, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
    color = Gold, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 12.dp)
)

@Composable
fun Stat(label: String, value: String, mod: Modifier = Modifier) {
    Card(mod, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold)
        }
    }
}

@Composable
fun Thumb(path: String?, mod: Modifier) {
    Box(mod.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (path != null) AsyncImage(model = File(path), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.ShoppingCart, null, tint = Muted)
    }
}

@Composable
fun Dashboard(vm: StoreVM) {
    val ps by vm.products.collectAsState()
    val ss by vm.sales.collectAsState()
    val start = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val today = ss.filter { it.date >= start }
    val low = ps.filter { it.stock <= 5 }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.info.logoPath != null) {
                    Thumb(vm.info.logoPath, Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(vm.info.name, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, color = Gold)
                    Text(vm.info.address.ifBlank { "Kelola toko dengan elegan" }, color = Muted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Penjualan hari ini", rp(today.sumOf { it.total }), Modifier.weight(1f))
                Stat("Transaksi hari ini", "${today.size}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Total produk", "${ps.size}", Modifier.weight(1f))
                Stat("Nilai stok", rp(ps.sumOf { it.price * it.stock }), Modifier.weight(1f))
            }
        }
        item { Stat("Total pendapatan", rp(ss.sumOf { it.total }), Modifier.fillMaxWidth()) }
        item { Text("Stok menipis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (low.isEmpty()) item { Text("Semua stok aman ✓", color = Muted) }
        else items(low, key = { it.id }) {
            Row(Modifier.fillMaxWidth()) {
                Text(it.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Sisa ${it.stock}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SearchBar(q: String, onChange: (String) -> Unit) = OutlinedTextField(
    q, onChange, singleLine = true, placeholder = { Text("Cari produk…") },
    leadingIcon = { Icon(Icons.Filled.Search, null) },
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = MaterialTheme.shapes.large
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(vm: StoreVM) {
    val ps by vm.products.collectAsState()
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Semua") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var show by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Product?>(null) }
    val cats = listOf("Semua") + ps.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val list = ps.filter { (cat == "Semua" || it.category == cat) && it.name.contains(q, true) }

    Box(Modifier.fillMaxSize()) {
        Column {
            Header("Produk")
            SearchBar(q) { q = it }
            LazyRow(contentPadding = PaddingValues(20.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
            }
            if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Belum ada produk. Tekan + untuk menambah.", color = Muted)
            } else LazyColumn(
                contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.id }) { p ->
                    Card(shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Thumb(p.imagePath, Modifier.size(76.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p.category.isNotBlank()) Text(p.category, style = MaterialTheme.typography.bodySmall, color = Muted)
                                Text(rp(p.price), color = Gold, fontWeight = FontWeight.SemiBold)
                                Text("Stok: ${p.stock}", style = MaterialTheme.typography.bodySmall,
                                    color = if (p.stock <= 5) MaterialTheme.colorScheme.error else Muted)
                            }
                            Column {
                                IconButton({ editing = p; show = true }) { Icon(Icons.Filled.Edit, "Ubah") }
                                IconButton({ del = p }) { Icon(Icons.Filled.Delete, "Hapus") }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = null; show = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Gold, contentColor = Color.Black
        ) { Icon(Icons.Filled.Add, "Tambah produk") }
    }
    if (show) ProductSheet(editing, { show = false }) { p, u -> vm.save(p, u); show = false }
    del?.let { p ->
        AlertDialog(onDismissRequest = { del = null },
            title = { Text("Hapus produk?") }, text = { Text(p.name) },
            confirmButton = { TextButton({ vm.delete(p); del = null }) { Text("Hapus") } },
            dismissButton = { TextButton({ del = null }) { Text("Batal") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSheet(p: Product?, onClose: () -> Unit, onSave: (Product, Uri?) -> Unit) {
    var name by remember { mutableStateOf(p?.name ?: "") }
    var cat by remember { mutableStateOf(p?.category ?: "") }
    var price by remember { mutableStateOf(p?.price?.toString() ?: "") }
    var stock by remember { mutableStateOf(p?.stock?.toString() ?: "") }
    var desc by remember { mutableStateOf(p?.description ?: "") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) uri = it }

    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (p == null) "Tambah Produk" else "Ubah Produk",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center) {
                val model: Any? = uri ?: p?.imagePath?.let { File(it) }
                if (model != null) AsyncImage(model = model, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("Ketuk untuk upload foto", color = Muted)
            }
            OutlinedTextField(name, { name = it }, label = { Text("Nama produk") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cat, { cat = it }, label = { Text("Kategori") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Harga (Rp)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(stock, { stock = it.filter(Char::isDigit) }, label = { Text("Stok") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(desc, { desc = it }, label = { Text("Deskripsi") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val pr = price.toLongOrNull()
                    if (name.isBlank()) err = "Nama produk wajib diisi"
                    else if (pr == null) err = "Harga wajib diisi"
                    else onSave(
                        (p ?: Product(name = "", category = "", price = 0, stock = 0, description = "", imagePath = null))
                            .copy(name = name.trim(), category = cat.trim(), price = pr,
                                stock = stock.toIntOrNull() ?: 0, description = desc.trim()), uri)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
            ) { Text("Simpan", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun Kasir(vm: StoreVM) {
    val ps by vm.products.collectAsState()
    var q by remember { mutableStateOf("") }
    val cart = vm.cart
    val ctx = LocalContext.current
    val list = ps.filter { it.name.contains(q, true) }
    val total = ps.sumOf { (cart[it.id] ?: 0) * it.price }
    val count = cart.values.sum()
    var confirm by remember { mutableStateOf(false) }
    var struk by remember { mutableStateOf<Sale?>(null) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Konfirmasi pembayaran") },
        text = { Text("Apakah Anda sudah yakin ingin melanjutkan?\n\nTotal: ${rp(total)}") },
        confirmButton = { TextButton({ confirm = false; vm.checkout { struk = it } }) { Text("Ya") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Tidak") } }
    )
    struk?.let { StrukDialog(vm, it) { struk = null } }
    Column(Modifier.fillMaxSize()) {
        Header("Kasir")
        SearchBar(q) { q = it }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { p ->
                val n = cart[p.id] ?: 0
                Card(shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(p.imagePath, Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${rp(p.price)} • stok ${p.stock}", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        if (n == 0) FilledTonalButton({ vm.add(p) }, enabled = p.stock > 0) {
                            Text(if (p.stock > 0) "Tambah" else "Habis")
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ vm.remove(p) }) { Text("−", style = MaterialTheme.typography.titleLarge, color = Gold) }
                            Text("$n", fontWeight = FontWeight.Bold)
                            IconButton({ vm.add(p) }) { Icon(Icons.Filled.Add, "Tambah", tint = Gold) }
                        }
                    }
                }
            }
        }
        if (count > 0) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$count item", color = Muted)
                    Text(rp(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold)
                }
                Button({ confirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) {
                    Text("Bayar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
