package com.example.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_ayahs", primaryKeys = ["surahNumber", "ayahNumber"])
data class FavoriteAyah(
    val surahNumber: Int,
    val ayahNumber: Int,
    val surahName: String,
    val surahEnglishName: String,
    val arabicText: String,
    val englishTranslation: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteAyahDao {
    @Query("SELECT * FROM favorite_ayahs ORDER BY timestamp DESC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteAyah>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteAyah)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteAyah)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_ayahs WHERE surahNumber = :surahNum AND ayahNumber = :ayahNum)")
    suspend fun isFavorite(surahNum: Int, ayahNum: Int): Boolean
}

@Database(entities = [FavoriteAyah::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteAyahDao(): FavoriteAyahDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quran_kareem_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
