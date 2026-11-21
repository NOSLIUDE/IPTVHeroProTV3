package com.iptvhero.pro.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val universe: String = "marvel", // "marvel" ou "dc"
    val sourceType: String = "m3u", // "xtream", "m3u", "txt"
    val sourceLabel: String = "",
    val server: String? = null,
    val user: String? = null,
    val pass: String? = null,
    val validityIso: String? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int = 0,
    val liveCount: Int = 0,
    val vodCount: Int = 0,
    val seriesCount: Int = 0,
    val bulkOrigin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
