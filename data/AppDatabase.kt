package com.ringhud.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity(tableName = "gesture_records")
data class GestureRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val gestureType: String,
    val context: String = ""      // optional: e.g. which screen was active
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface GestureDao {

    @Insert
    suspend fun insert(record: GestureRecord)

    @Query("SELECT * FROM gesture_records ORDER BY timestampMs DESC LIMIT 100")
    fun recentGestures(): Flow<List<GestureRecord>>

    @Query("DELETE FROM gesture_records WHERE timestampMs < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

// ── Preferences (ring MAC + HUD settings) ────────────────────────────────────

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSetting)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities  = [GestureRecord::class, AppSetting::class],
    version   = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gestureDao(): GestureDao
    abstract fun settingsDao(): SettingsDao
}
