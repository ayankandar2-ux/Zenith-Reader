package com.example.data.dao

import androidx.room.*
import com.example.data.model.PreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE `key` = :key")
    suspend fun getPreference(key: String): PreferenceEntity?

    @Query("SELECT * FROM preferences WHERE `key` = :key")
    fun getPreferenceFlow(key: String): Flow<PreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: PreferenceEntity)

    @Query("DELETE FROM preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)
}
