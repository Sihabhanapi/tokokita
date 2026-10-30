package com.xyoruz.tokokita

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotaCfg(
    val showLogo: Boolean = true,
    val showAddress: Boolean = true,
    val showPhone: Boolean = true,
    val header: String = "",
    val footer: String = "Terima kasih telah berbelanja",
    val paper: Int = 58
)

data class Line(val name: String, val qty: Int, val price: Long)

/** Format baru: "nama<TAB>qty<TAB>harga" per baris. Null = data lama (teks biasa). */
fun parseLines(items: String): List<Line>? {
    val out = items.lines().filter { it.isNotBlank() }.map { l ->
        val p = l.split('\t')
        if (p.size != 3) return null
        Line(p[0], p[1].toIntOrNull() ?: return null, p[2].toLongOrNull() ?: return null)
    }
    return if (out.isEmpty()) null else out
}

fun itemsLabel(items: String): String = items.lines().joinToString("\n") { l ->
    val p = l.split('\t')
    if (p.size == 3) "${p[0]} × ${p[1]} @ ${rp(p[2].toLongOrNull() ?: 0)}" else l
}

fun previewSale() = Sale(
    id = 1, date = System.currentTimeMillis(), total = 45000,
    items = "Contoh Produk A\t2\t15000\nContoh Produk B\t1\t15000"
)

private val dateFmt get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))

fun receiptText(info: StoreInfo, c: NotaCfg, s: Sale): String = buildString {
    val line = "-".repeat(if (c.paper == 58) 32 else 42)
    appendLine(info.name)
    if (c.showAddress && info.address.isNotBlank()) appendLine(info.address)
    if (c.showPhone && info.phone.isNotBlank()) appendLine("Telp: ${info.phone}")
    if (c.header.isNotBlank()) appendLine(c.header)
    appendLine(line)
    appendLine("No: #${s.id}  ${dateFmt.format(Date(s.date))}")
    appendLine(line)
    val ls = parseLines(s.items)
    if (ls != null) {
        ls.forEach { appendLine(it.name); appendLine("  ${it.qty} x ${rp(it.price)} = ${rp(it.price * it.qty)}") }
    } else appendLine(s.items)
    appendLine(line)
    appendLine("TOTAL: ${rp(s.total)}")
    if (c.footer.isNotBlank()) { appendLine(); append(c.footer) }
}

private fun logoUri(path: String?): String? {
    if (path == null) return null
    return try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var sample = 1
        while (o.outWidth / sample > 300) sample *= 2
        val bm = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val bos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { null }
}

fun receiptHtml(info: StoreInfo, c: NotaCfg, s: Sale): String {
    fun e(x: String) = x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val w = if (c.paper == 58) 54 else 76
    val ls = parseLines(s.items)
    val logo = if (c.showLogo) logoUri(info.logoPath) else null
    return buildString {
        append("<html><head><meta charset='utf-8'><style>")
        append("body{font-family:monospace;font-size:12px;color:#000;width:${w}mm;margin:0 auto}")
        append(".c{text-align:center}.b{font-weight:bold}.r{text-align:right}")
        append("hr{border:0;border-top:1px dashed #000}table{width:100%;border-collapse:collapse}td{vertical-align:top}")
        append("</style></head><body>")
        if (logo != null) append("<div class='c'><img src='$logo' height='60'></div>")
        append("<div class='c b' style='font-size:15px'>${e(info.name)}</div>")
        if (c.showAddress && info.address.isNotBlank()) append("<div class='c'>${e(info.address)}</div>")
        if (c.showPhone && info.phone.isNotBlank()) append("<div class='c'>Telp: ${e(info.phone)}</div>")
        if (c.header.isNotBlank()) append("<div class='c'>${e(c.header)}</div>")
        append("<hr><div>No: #${s.id}<br>${dateFmt.format(Date(s.date))}</div><hr><table>")
        if (ls != null) ls.forEach {
            append("<tr><td colspan='2'>${e(it.name)}</td></tr>")
            append("<tr><td>${it.qty} x ${rp(it.price)}</td><td class='r'>${rp(it.price * it.qty)}</td></tr>")
        } else append("<tr><td colspan='2'>${e(s.items).replace("\n", "<br>")}</td></tr>")
        append("</table><hr><table><tr class='b'><td>TOTAL</td><td class='r'>${rp(s.total)}</td></tr></table>")
        if (c.footer.isNotBlank()) append("<br><div class='c'>${e(c.footer)}</div>")
        append("</body></html>")
    }
}

private var keepWebView: WebView? = null

/** Membuka dialog cetak Android: bisa ke printer atau "Simpan sebagai PDF". */
fun printReceipt(ctx: Context, info: StoreInfo, c: NotaCfg, s: Sale) {
    val wv = WebView(ctx)
    keepWebView = wv
    var printed = false
    wv.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            if (printed) return
            printed = true
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print("Struk-${s.id}", view.createPrintDocumentAdapter("Struk-${s.id}"), PrintAttributes.Builder().build())
        }
    }
    wv.loadDataWithBaseURL(null, receiptHtml(info, c, s), "text/html", "UTF-8", null)
}

@Composable
private fun Ink(t: String, mod: Modifier = Modifier, bold: Boolean = false, size: Int = 12, align: TextAlign = TextAlign.Start) =
    Text(t, mod, color = Color(0xFF1B1B1B), fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp, textAlign = align)

@Composable
fun ReceiptView(info: StoreInfo, c: NotaCfg, s: Sale) {
    val ls = remember(s) { parseLines(s.items) }
    Column(
        Modifier.fillMaxWidth().widthIn(max = if (c.paper == 58) 260.dp else 340.dp)
            .clip(RoundedCornerShape(6.dp)).background(Color.White).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (c.showLogo && info.logoPath != null) {
            AsyncImage(model = File(info.logoPath), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(6.dp))
        }
        Ink(info.name, bold = true, size = 16, align = TextAlign.Center)
        if (c.showAddress && info.address.isNotBlank()) Ink(info.address, align = TextAlign.Center)
        if (c.showPhone && info.phone.isNotBlank()) Ink("Telp: ${info.phone}", align = TextAlign.Center)
        if (c.header.isNotBlank()) Ink(c.header, align = TextAlign.Center)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Gray)
        Ink("No: #${s.id}", Modifier.fillMaxWidth())
        Ink(dateFmt.format(Date(s.date)), Modifier.fillMaxWidth())
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Gray)
        if (ls != null) ls.forEach { l ->
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Ink(l.name)
                Row {
                    Ink("${l.qty} x ${rp(l.price)}", Modifier.weight(1f))
                    Ink(rp(l.price * l.qty))
                }
            }
        } else Ink(s.items, Modifier.fillMaxWidth())
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Gray)
        Row(Modifier.fillMaxWidth()) {
            Ink("TOTAL", Modifier.weight(1f), bold = true, size = 14)
            Ink(rp(s.total), bold = true, size = 14)
        }
        if (c.footer.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Ink(c.footer, align = TextAlign.Center)
        }
    }
}

@Composable
fun StrukDialog(vm: StoreVM, s: Sale, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Text("Struk Pembelian", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Gold)
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), Alignment.TopCenter) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { ReceiptView(vm.info, vm.nota, s) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ printReceipt(ctx, vm.info, vm.nota, s) }, Modifier.weight(1f)) { Text("Cetak") }
                    Button({
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, receiptText(vm.info, vm.nota, s))
                        }
                        ctx.startActivity(Intent.createChooser(i, "Bagikan struk"))
                    }, Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("Bagikan") }
                }
                TextButton(onClose, Modifier.fillMaxWidth()) { Text("Tutup") }
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, v: Boolean, on: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(v, on, colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color.Black))
    }
