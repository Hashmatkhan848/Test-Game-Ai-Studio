package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Voyage History Entity (Past Runs)
@Entity(tableName = "voyage_history")
data class VoyageHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val score: Int,
    val shardsCollected: Int,
    val sectorReached: Int,
    val shipType: String,
    val timestamp: Long = System.currentTimeMillis()
)

// 2. Player Progress & Persistent RPG Upgrades Entity
@Entity(tableName = "player_progress")
data class PlayerProgress(
    @PrimaryKey val id: Int = 1,
    val credits: Int = 0,            // Shards balance to spend in shop
    val highScore: Int = 0,
    val unlockedShips: String = "Stinger-X", // Comma-separated ship IDs
    val selectedShip: String = "Stinger-X",
    val laserLevel: Int = 1,         // Damage / fire rate Level
    val shieldLevel: Int = 0,        // Adds shield capacity & auto regen
    val maxHpLevel: Int = 1,         // Hull rating level
    val thrusterLevel: Int = 1,      // Speed level
    val magnetLevel: Int = 1         // Shard attraction radius level
)

// 3. Data Access Object (DAO)
@Dao
interface GameDao {
    @Query("SELECT * FROM voyage_history ORDER BY timestamp DESC")
    fun getVoyageHistory(): Flow<List<VoyageHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoyage(history: VoyageHistory)

    @Query("DELETE FROM voyage_history")
    suspend fun clearVoyageHistory()

    @Query("SELECT * FROM player_progress WHERE id = 1 LIMIT 1")
    fun getProgressFlow(): Flow<PlayerProgress?>

    @Query("SELECT * FROM player_progress WHERE id = 1 LIMIT 1")
    suspend fun getProgressDirect(): PlayerProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlayerProgress)
}

// 4. Room Database
@Database(entities = [VoyageHistory::class, PlayerProgress::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "galactic_rift_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// 5. Repository Abstraction (mandated by room-database-integration skill)
class GameRepository(private val dao: GameDao) {
    val voyageHistory: Flow<List<VoyageHistory>> = dao.getVoyageHistory()
    val playerProgress: Flow<PlayerProgress?> = dao.getProgressFlow()

    suspend fun insertVoyage(voyage: VoyageHistory) {
        dao.insertVoyage(voyage)
        // Also update High Score auto-actively if applicable
        val progress = dao.getProgressDirect() ?: PlayerProgress()
        if (voyage.score > progress.highScore) {
            dao.saveProgress(progress.copy(highScore = voyage.score))
        }
    }

    suspend fun clearHistory() {
        dao.clearVoyageHistory()
    }

    suspend fun getCurrentProgress(): PlayerProgress {
        return dao.getProgressDirect() ?: PlayerProgress().also {
            dao.saveProgress(it)
        }
    }

    suspend fun saveProgress(progress: PlayerProgress) {
        dao.saveProgress(progress)
    }

    suspend fun addCredits(amount: Int) {
        val curr = getCurrentProgress()
        dao.saveProgress(curr.copy(credits = curr.credits + amount))
    }
}
