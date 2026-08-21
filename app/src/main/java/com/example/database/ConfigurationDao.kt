package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigurationDao {
    @Query("SELECT * FROM app_configuration WHERE id = 1 LIMIT 1")
    suspend fun getConfiguration(): AppConfiguration?

    @Query("SELECT * FROM app_configuration WHERE id = 1 LIMIT 1")
    fun getConfigurationFlow(): Flow<AppConfiguration?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfiguration(config: AppConfiguration)

    @Query("UPDATE app_configuration SET autoBuyEnabled = :enabled WHERE id = 1")
    suspend fun updateAutoBuyEnabled(enabled: Boolean)
}
