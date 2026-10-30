package com.xyoruz.tokokita

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val price: Long,
    val stock: Int,
    val description: String,
    val imagePath: String?
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val total: Long,
    val items: String
)

@Dao
abstract class StoreDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    abstract fun products(): Flow<List<Product>>

    @Query("SELECT * FROM sales ORDER BY date DESC")
    abstract fun sales(): Flow<List<Sale>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(p: Product)

    @Delete
    abstract suspend fun delete(p: Product)

    @Insert
    abstract suspend fun insertSale(s: Sale): Long

    @Query("UPDATE products SET stock = MAX(stock - :qty, 0) WHERE id = :id")
    abstract suspend fun reduceStock(id: Long, qty: Int)

    @Query("SELECT * FROM products") abstract suspend fun allProducts(): List<Product>
    @Query("SELECT * FROM sales") abstract suspend fun allSales(): List<Sale>
    @Query("DELETE FROM products") abstract suspend fun clearProducts()
    @Query("DELETE FROM sales") abstract suspend fun clearSales()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertProducts(l: List<Product>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSales(l: List<Sale>)

    @Transaction
    open suspend fun restore(p: List<Product>, s: List<Sale>) {
        clearProducts(); clearSales(); insertProducts(p); insertSales(s)
    }

    @Transaction
    open suspend fun checkout(sale: Sale, cart: Map<Long, Int>): Long {
        val id = insertSale(sale)
        cart.forEach { (pid, q) -> reduceStock(pid, q) }
        return id
    }
}

@Database(entities = [Product::class, Sale::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): StoreDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "tokokita.db")
                .build().also { inst = it }
        }
    }
}
