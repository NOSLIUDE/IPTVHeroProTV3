package com.iptvhero.pro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY lastUsed DESC, id DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ProfileEntity?

    @Query("UPDATE profiles SET isActive = 0")
    suspend fun clearActiveProfiles()

    @Query("UPDATE profiles SET isActive = 1, lastUsed = :timestamp WHERE id = :profileId")
    suspend fun setActiveProfileById(profileId: Long, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun setActiveProfile(profileId: Long) {
        clearActiveProfiles()
        setActiveProfileById(profileId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: Long)

    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()
}
