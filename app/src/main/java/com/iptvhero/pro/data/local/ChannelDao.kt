package com.iptvhero.pro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels WHERE profileId = :profileId ORDER BY id ASC")
    fun getChannelsByProfile(profileId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT COUNT(*) FROM channels WHERE profileId = :profileId")
    suspend fun countChannelsByProfile(profileId: Long): Int

    @Query("DELETE FROM channels WHERE profileId = :profileId")
    suspend fun deleteChannelsByProfile(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)
}
