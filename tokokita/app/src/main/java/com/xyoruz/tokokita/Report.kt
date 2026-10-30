package com.xyoruz.tokokita

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ID = Locale("id", "ID")

// ---------- tanggal ----------
fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun startOfMonth(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(ms); set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

fun addDays(ms: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.DAY_OF_MONTH, n) }.timeInMillis
fun addMonths(ms: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.MONTH, n) }.timeInMillis

fun parseInput(d: String, monthly: Boolean): Long? = try {
    SimpleDateFormat(if (monthly) "MMyyyy" else "ddMMyyyy", Locale.US).apply { isLenient = false }.parse(d)?.time
} catch (e: Exception) { null }

/** Menyisipkan "-" otomatis: 20082027 -> 20-08-2027 */
class DashTransform(private val pos: List<Int>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val sb = StringBuilder()
        d.forEachIndexed { i, c -> if (i in pos) sb.append('-'); sb.append(c) }
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + pos.count { it < offset }
            override fun transformedToOriginal(offset: Int): Int {
                var n = 0
                pos.forEachIndexed { k, p -> if (p + k < offset) n++ }
                return (offset - n).coerceIn(0, d.length)
            }
        }
        return TransformedText(AnnotatedString(sb.toString()), map)
    }
}

// ---------- data laporan ----------
data class Period(val label: String, val count: Int, val total: Long, val start: Long = 0)

fun periods(ss: List<Sale>, monthly: Boolean): List<Period> {
    val key = SimpleDateFormat(if (monthly) "yyyy-MM" else "yyyy-MM-dd", Locale.US)
    val lab = SimpleDateFormat(if (monthly) "MMMM yyyy" else "EEEE, dd MMM yyyy", ID)
    return ss.groupBy { key.format(Date(it.date)) }
        .toSortedMap(reverseOrder<String>()).values
        .map { l ->
            val d = l.first().date
            Period(lab.format(Date(d)), l.size, l.sumOf { it.total }, if (monthly) startOfMonth(d) else startOfDay(d))
        }
}

fun itemCount(s: Sale): Int = parseLines(s.items)?.sumOf { it.qty } ?: 0

fun topProducts(list: List<Sale>): List<Pair<String, Int>> =
    list.flatMap { parseLines(it.items) ?: emptyList() }
        .groupBy { it.name }.mapValues { (_, v) -> v.sumOf { it.qty } }
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }

// Pemisah ";" agar terbuka rapi di Excel (locale Indonesia) & Google Sheets
fun csvRow(vararg c: Any): String = c.joinToString(";") { v ->
    var s = v.toString().replace("\n", " | ")
    if (v is String && s.isNotEmpty() && s[0] in "=+-@") s = "'$s"
    if (s.contains(';') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

fun summaryCsv(store: String, rows: List<Period>, monthly: Boolean): String = buildString {
    append(csvRow(store)).append("\r\n")
    append(csvRow(if (monthly) "Rekap Bulanan" else "Rekap Harian")).append("\r\n\r\n")
    append(csvRow("Periode", "Jumlah Transaksi", "Total Penjualan (Rp)")).append("\r\n")
    rows.forEach { append(csvRow(it.label, it.count, it.total)).append("\r\n") }
    append(csvRow("TOTAL", rows.sumOf { it.count }, rows.sumOf { it.total }))
}

fun detailCsv(ss: List<Sale>): String {
    val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return (listOf(csvRow("Tanggal", "Item", "Total (Rp)")) +
        ss.map { csvRow(f.format(Date(it.date)), itemsLabel(it.items), it.total) }).joinToString("\r\n")
}

// ---------- UI ----------
@Composable
fun EmptyNote(t: String) = Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
    Text(t, color = Muted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateNav(value: Long, monthly: Boolean, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    val max = if (monthly) 6 else 8
    var input by remember(value, monthly) {
        mutableStateOf(SimpleDateFormat(if (monthly) "MMyyyy" else "ddMMyyyy", Locale.US).format(Date(value)))
    }
    val bad = input.length == max && parseInput(input, monthly) == null
    val step = { n: Int -> onChange(if (monthly) addMonths(value, n) else addDays(value, n)) }
    val now = System.currentTimeMillis()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ step(-1) }) { Text("‹", style = MaterialTheme.typography.headlineMedium, color = Gold) }
            OutlinedTextField(
                input,
                { v ->
                    val d = v.filter(Char::isDigit).take(max)
                    input = d
                    if (d.length == max) parseInput(d, monthly)?.let(onChange)
                },
                Modifier.weight(1f), singleLine = true, isError = bad,
                label = { Text(if (monthly) "Bulan (mm-yyyy)" else "Tanggal (dd-mm-yyyy)") },
                supportingText = {
                    Text(if (bad) "Tanggal tidak valid" else if (monthly) "Contoh: 08-2027" else "Contoh: 20-08-2027")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = remember(monthly) { DashTransform(if (monthly) listOf(2) else listOf(2, 4)) },
                trailingIcon = {
                    IconButton({
                        val c = Calendar.getInstance().apply { timeInMillis = value }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            onChange(Calendar.getInstance().apply { clear(); set(y, m, if (monthly) 1 else d) }.timeInMillis)
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Icon(Icons.Filled.DateRange, "Pilih dari kalender", tint = Gold) }
                }
            )
            IconButton({ step(1) }) { Text("›", style = MaterialTheme.typography.headlineMedium, color = Gold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (monthly) {
                val a = startOfMonth(now); val b = startOfMonth(addMonths(now, -1))
                FilterChip(selected = value == a, onClick = { onChange(a) }, label = { Text("Bulan ini") })
                FilterChip(selected = value == b, onClick = { onChange(b) }, label = { Text("Bulan lalu") })
            } else {
                val a = startOfDay(now); val b = startOfDay(addDays(now, -1))
                FilterChip(selected = value == a, onClick = { onChange(a) }, label = { Text("Hari ini") })
                FilterChip(selected = value == b, onClick = { onChange(b) }, label = { Text("Kemarin") })
            }
        }
    }
}

@Composable
fun TxCard(vm: StoreVM, s: Sale, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val fmt = remember { SimpleDateFormat("HH:mm", ID) }
    Card(Modifier.clickable { onOpen() }, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${s.id} • ${fmt.format(Date(s.date))}", Modifier.weight(1f), color = Muted)
                Text(rp(s.total), color = Gold, fontWeight = FontWeight.Bold)
                IconButton({
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, receiptText(vm.info, vm.nota, s))
                    }
                    ctx.startActivity(Intent.createChooser(i, "Bagikan struk"))
                }) { Icon(Icons.Filled.Share, "Bagikan struk", tint = Gold) }
            }
            Text(itemsLabel(s.items), Modifier.padding(end = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: StoreVM) {
    val all by vm.sales.collectAsState()
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 hari, 1 bulan, 2 semua
    var day by remember { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    var month by remember { mutableLongStateOf(startOfMonth(System.currentTimeMillis())) }
    var struk by remember { mutableStateOf<Sale?>(null) }
    var pending by remember { mutableStateOf("") }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri, pending) { ok ->
            Toast.makeText(ctx, if (ok) "Berhasil diekspor" else "Gagal mengekspor", Toast.LENGTH_SHORT).show()
        }
    }
    fun startExport(name: String, text: String) { pending = text; saver.launch(name) }

    val from = when (mode) { 0 -> day; 1 -> month; else -> Long.MIN_VALUE }
    val to = when (mode) { 0 -> addDays(day, 1); 1 -> addMonths(month, 1); else -> Long.MAX_VALUE }
    val list = remember(all, mode, day, month) { all.filter { it.date >= from && it.date < to } }
    val breakdown = remember(list, mode) {
        when (mode) { 1 -> periods(list, false); 2 -> periods(list, true); else -> emptyList() }
    }
    val total = list.sumOf { it.total }
    val top = topProducts(list)
    val title = when (mode) {
        0 -> SimpleDateFormat("EEEE, dd MMMM yyyy", ID).format(Date(day))
        1 -> SimpleDateFormat("MMMM yyyy", ID).format(Date(month))
        else -> "Semua waktu"
    }
    val tag = when (mode) {
        0 -> SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(day))
        1 -> SimpleDateFormat("MM-yyyy", Locale.US).format(Date(month))
        else -> "semua"
    }
    struk?.let { StrukDialog(vm, it) { struk = null } }

    Column(Modifier.fillMaxSize()) {
        Header("Laporan")
        LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hari", "Bulan", "Semua").forEachIndexed { i, l ->
                        FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(l) })
                    }
                }
            }
            if (mode == 0) item { DateNav(day, false) { day = startOfDay(it) } }
            if (mode == 1) item { DateNav(month, true) { month = startOfMonth(it) } }
            item { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Penjualan", rp(total), Modifier.weight(1f))
                    Stat("Transaksi", "${list.size}", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Item terjual", "${list.sumOf { itemCount(it) }}", Modifier.weight(1f))
                    Stat("Rata-rata", rp(if (list.isEmpty()) 0L else total / list.size), Modifier.weight(1f))
                }
            }
            if (top.isNotEmpty()) item {
                Card(shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Produk terlaris", style = MaterialTheme.typography.labelMedium, color = Muted)
                        top.forEachIndexed { i, (n, q) ->
                            Row {
                                Text("${i + 1}. $n", Modifier.weight(1f))
                                Text("$q terjual", color = Gold, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ startExport("transaksi-$tag.csv", detailCsv(list)) },
                        Modifier.weight(1f), enabled = list.isNotEmpty()) { Text("Ekspor Transaksi") }
                    if (mode != 0) OutlinedButton({
                        startExport(if (mode == 1) "rekap-harian-$tag.csv" else "rekap-bulanan.csv",
                            summaryCsv(vm.info.name, breakdown, mode == 2))
                    }, Modifier.weight(1f), enabled = breakdown.isNotEmpty()) { Text("Ekspor Rekap") }
                }
            }
            if (mode == 0) {
                if (list.isEmpty()) item { EmptyNote("Tidak ada transaksi pada $tag") }
                else {
                    item { Text("Daftar transaksi (${list.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(list, key = { it.id }) { s -> TxCard(vm, s) { struk = s } }
                }
            } else {
                item {
                    Column {
                        Text(if (mode == 1) "Rekap per hari" else "Rekap per bulan",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Ketuk untuk melihat detail transaksinya", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                if (breakdown.isEmpty()) item { EmptyNote("Belum ada transaksi pada periode ini") }
                else items(breakdown) { p ->
                    Card(Modifier.clickable { if (mode == 1) { day = p.start; mode = 0 } else { month = p.start; mode = 1 } },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label, fontWeight = FontWeight.SemiBold)
                                Text("${p.count} transaksi", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            Text(rp(p.total), color = Gold, fontWeight = FontWeight.Bold)
                            Text("  ›", color = Muted)
                        }
                    }
                }
            }
        }
    }
}
