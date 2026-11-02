package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedWorkEntity::class],
    version = 4,
    exportSchema = true
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao

    companion object {
        @Volatile
        private var INSTANCE: CacheDatabase? = null

        fun getDatabase(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "cache.db"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Database(
    entities = [LibraryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        private const val TAG = "LibraryDatabase"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_works ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE library_works ADD COLUMN duration INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE library_works ADD COLUMN nextEpisodeAt TEXT DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: LibraryDatabase? = null

        fun getDatabase(context: Context): LibraryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "library.db"
                )
                .addMigrations(MIGRATION_1_2)
                // STRICTLY NO fallbackToDestructiveMigration to protect user data!
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        migrateOldDatabaseIfPresent(context, db)
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun migrateOldDatabaseIfPresent(context: Context, newDb: SupportSQLiteDatabase) {
            val oldDbFile = context.getDatabasePath("otakuverse_local.db")
            if (!oldDbFile.exists()) return

            try {
                val oldDb = SQLiteDatabase.openDatabase(oldDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = oldDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='library_works'", null)
                val hasTable = cursor.moveToFirst()
                cursor.close()

                if (hasTable) {
                    val dataCursor = oldDb.rawQuery("SELECT * FROM library_works", null)
                    while (dataCursor.moveToNext()) {
                        val id = dataCursor.getString(dataCursor.getColumnIndexOrThrow("id"))
                        val listType = dataCursor.getString(dataCursor.getColumnIndexOrThrow("listType"))
                        val isFav = listType.equals("FAVORITES", ignoreCase = true)
                        val status = when (listType.uppercase()) {
                            "READING" -> "READING"
                            "COMPLETED" -> "COMPLETED"
                            else -> "PLANNING"
                        }
                        val workType = dataCursor.getString(dataCursor.getColumnIndexOrThrow("workType"))
                        val format = dataCursor.getString(dataCursor.getColumnIndexOrThrow("format"))
                        val title = dataCursor.getString(dataCursor.getColumnIndexOrThrow("title"))
                        val titleEn = dataCursor.getString(dataCursor.getColumnIndexOrThrow("titleEn"))
                        val cover = dataCursor.getString(dataCursor.getColumnIndexOrThrow("cover"))
                        val coverColor = dataCursor.getString(dataCursor.getColumnIndexOrThrow("coverColor"))
                        val avgScore = if (dataCursor.isNull(dataCursor.getColumnIndexOrThrow("avgScore"))) null else dataCursor.getInt(dataCursor.getColumnIndexOrThrow("avgScore"))
                        val workStatus = dataCursor.getString(dataCursor.getColumnIndexOrThrow("status"))
                        val genresJson = dataCursor.getString(dataCursor.getColumnIndexOrThrow("genresJson"))
                        val progress = dataCursor.getInt(dataCursor.getColumnIndexOrThrow("currentProgress"))
                        val total = if (dataCursor.isNull(dataCursor.getColumnIndexOrThrow("totalProgress"))) null else dataCursor.getInt(dataCursor.getColumnIndexOrThrow("totalProgress"))
                        val userRating = if (dataCursor.isNull(dataCursor.getColumnIndexOrThrow("userRating"))) null else dataCursor.getInt(dataCursor.getColumnIndexOrThrow("userRating"))
                        val updatedAt = dataCursor.getLong(dataCursor.getColumnIndexOrThrow("updatedAt"))

                        newDb.execSQL(
                            """
                            INSERT OR REPLACE INTO library_works 
                            (id, status, isFavorite, currentProgress, totalProgress, userRating, addedAt, updatedAt, workType, format, title, titleEn, cover, coverColor, avgScore, workStatus, genresJson)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent(),
                            arrayOf<Any?>(
                                id, status, if (isFav) 1 else 0, progress, total, userRating, updatedAt, updatedAt,
                                workType, format, title, titleEn, cover, coverColor, avgScore, workStatus, genresJson
                            )
                        )
                    }
                    dataCursor.close()
                    Log.i(TAG, "Successfully migrated records from otakuverse_local.db to library.db")
                }
                oldDb.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not migrate from old database: ${e.message}")
            }
        }
    }
}
