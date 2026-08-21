package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseRecord): Long

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseRecord>>

    @Query("DELETE FROM purchases WHERE id = (SELECT id FROM purchases ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLatestPurchase()

    @Query("DELETE FROM purchases")
    suspend fun clearAll()
}
