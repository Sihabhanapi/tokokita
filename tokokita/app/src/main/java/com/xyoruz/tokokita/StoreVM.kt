package com.xyoruz.tokokita

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class StoreInfo(
    val name: String = "TokoKita",
    val address: String = "",
    val phone: String = "",
    val logoPath: String? = null
)

class StoreVM(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val prefs = app.getSharedPreferences("toko", Context.MODE_PRIVATE)
    val products = dao.products().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sales = dao.sales().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cart = mutableStateMapOf<Long, Int>()

    var info by mutableStateOf(
        StoreInfo(
            prefs.getString("name", "TokoKita") ?: "TokoKita",
            prefs.getString("address", "") ?: "",
            prefs.getString("phone", "") ?: "",
            prefs.getString("logo", null)
        )
    )
        private set

    var nota by mutableStateOf(
        NotaCfg(
            prefs.getBoolean("n_logo", true), prefs.getBoolean("n_addr", true), prefs.getBoolean("n_phone", true),
            prefs.getString("n_header", "") ?: "",
            prefs.getString("n_footer", "Terima kasih telah berbelanja") ?: "",
            prefs.getInt("n_paper", 58)
        )
    )
        private set

    fun saveNota(n: NotaCfg) {
        prefs.edit().putBoolean("n_logo", n.showLogo).putBoolean("n_addr", n.showAddress)
            .putBoolean("n_phone", n.showPhone).putString("n_header", n.header)
            .putString("n_footer", n.footer).putInt("n_paper", n.paper).apply()
        nota = n
    }

    private fun applyInfo(n: StoreInfo) {
        prefs.edit().putString("name", n.name).putString("address", n.address)
            .putString("phone", n.phone).putString("logo", n.logoPath).apply()
        info = n
    }

    private fun copyImage(uri: Uri): String? {
        val ctx = getApplication<Application>()
        val f = File(ctx.filesDir, "img_${System.currentTimeMillis()}.jpg")
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            f.absolutePath
        } catch (e: Exception) { null }
    }

    fun saveInfo(i: StoreInfo, logo: Uri?) = viewModelScope.launch {
        var lp = i.logoPath
        if (logo != null) {
            val np = withContext(Dispatchers.IO) { copyImage(logo) }
            if (np != null) { lp?.let { File(it).delete() }; lp = np }
        }
        applyInfo(i.copy(logoPath = lp))
    }

    fun save(p: Product, uri: Uri?) = viewModelScope.launch {
        var path = p.imagePath
        if (uri != null) {
            val newPath = withContext(Dispatchers.IO) { copyImage(uri) }
            if (newPath != null) {
                p.imagePath?.let { File(it).delete() }
                path = newPath
            }
        }
        dao.upsert(p.copy(imagePath = path))
    }

    fun delete(p: Product) = viewModelScope.launch {
        cart.remove(p.id)
        p.imagePath?.let { File(it).delete() }
        dao.delete(p)
    }

    fun add(p: Product) {
        val n = cart[p.id] ?: 0
        if (n < p.stock) cart[p.id] = n + 1
    }

    fun remove(p: Product) {
        val n = (cart[p.id] ?: 0) - 1
        if (n <= 0) cart.remove(p.id) else cart[p.id] = n
    }

    fun checkout(onDone: (Sale) -> Unit) {
        val c = cart.toMap()
        if (c.isEmpty()) return
        val byId = products.value.associateBy { it.id }
        val lines = c.entries.mapNotNull { (id, q) -> byId[id]?.let { it to q } }
        val total = lines.sumOf { (p, q) -> p.price * q }
        val desc = lines.joinToString("\n") { (p, q) ->
            "${p.name.replace('\t', ' ').replace('\n', ' ')}\t$q\t${p.price}"
        }
        viewModelScope.launch {
            val sale = Sale(date = System.currentTimeMillis(), total = total, items = desc)
            val id = dao.checkout(sale, c)
            cart.clear()
            onDone(sale.copy(id = id))
        }
    }

    fun export(uri: Uri, text: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM utf-8 untuk Excel
                    it.write(text.toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) { false }
        }
        onDone(ok)
    }

    fun backup(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val ps = dao.allProducts()
                val ss = dao.allSales()
                val root = JSONObject()
                root.put("version", 1)
                root.put("info", JSONObject()
                    .put("name", info.name).put("address", info.address).put("phone", info.phone)
                    .put("logo", info.logoPath?.let { File(it).name } ?: JSONObject.NULL))
                root.put("nota", JSONObject().put("logo", nota.showLogo).put("addr", nota.showAddress)
                    .put("phone", nota.showPhone).put("header", nota.header)
                    .put("footer", nota.footer).put("paper", nota.paper))
                val pa = JSONArray()
                ps.forEach { p ->
                    pa.put(JSONObject().put("id", p.id).put("name", p.name).put("category", p.category)
                        .put("price", p.price).put("stock", p.stock).put("description", p.description)
                        .put("image", p.imagePath?.let { File(it).name } ?: JSONObject.NULL))
                }
                root.put("products", pa)
                val sa = JSONArray()
                ss.forEach { s ->
                    sa.put(JSONObject().put("id", s.id).put("date", s.date)
                        .put("total", s.total).put("items", s.items))
                }
                root.put("sales", sa)
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: return@withContext false
                ZipOutputStream(out).use { z ->
                    z.putNextEntry(ZipEntry("data.json"))
                    z.write(root.toString().toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                    (ps.mapNotNull { it.imagePath } + listOfNotNull(info.logoPath)).distinct().forEach { path ->
                        val f = File(path)
                        if (f.exists()) {
                            z.putNextEntry(ZipEntry("images/${f.name}"))
                            f.inputStream().use { it.copyTo(z) }
                            z.closeEntry()
                        }
                    }
                }
                true
            } catch (e: Exception) { false }
        }
        onDone(ok)
    }

    fun restore(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val dir = ctx.filesDir
                var json: String? = null
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    ZipInputStream(ins).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            val n = File(e.name).name
                            if (e.name == "data.json") json = z.readBytes().toString(Charsets.UTF_8)
                            else if (e.name.startsWith("images/") && n.startsWith("img_"))
                                File(dir, n).outputStream().use { o -> z.copyTo(o) }
                            e = z.nextEntry
                        }
                    }
                }
                val root = JSONObject(json ?: return@withContext false)
                val pa = root.getJSONArray("products")
                val ps = (0 until pa.length()).map { i ->
                    val o = pa.getJSONObject(i)
                    Product(
                        o.getLong("id"), o.getString("name"), o.getString("category"),
                        o.getLong("price"), o.getInt("stock"), o.getString("description"),
                        if (o.isNull("image")) null else File(dir, o.getString("image")).absolutePath
                    )
                }
                val sa = root.getJSONArray("sales")
                val ss = (0 until sa.length()).map { i ->
                    val o = sa.getJSONObject(i)
                    Sale(o.getLong("id"), o.getLong("date"), o.getLong("total"), o.getString("items"))
                }
                dao.restore(ps, ss)
                val io = root.optJSONObject("info")
                withContext(Dispatchers.Main) {
                    cart.clear()
                    root.optJSONObject("nota")?.let { n ->
                        saveNota(NotaCfg(
                            n.optBoolean("logo", true), n.optBoolean("addr", true), n.optBoolean("phone", true),
                            n.optString("header", ""), n.optString("footer", ""), n.optInt("paper", 58)
                        ))
                    }
                    if (io != null) applyInfo(StoreInfo(
                        io.optString("name", "TokoKita"), io.optString("address", ""), io.optString("phone", ""),
                        if (io.isNull("logo")) null else File(dir, io.getString("logo")).absolutePath
                    ))
                }
                true
            } catch (e: Exception) { false }
        }
        onDone(ok)
    }
}
