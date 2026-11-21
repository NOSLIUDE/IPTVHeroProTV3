package com.iptvhero.pro.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val title: String,
    val url: String,
    val groupName: String = "Geral",
    val contentType: String = "LIVE", // LIVE, VOD, SERIES
    val streamIcon: String? = null,
    val remoteId: String? = null
)
