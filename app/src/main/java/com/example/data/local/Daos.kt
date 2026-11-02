package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
    @Query("SELECT * FROM cached_works WHERE section = :section AND cachedAt >= :minCachedAt ORDER BY position ASC")
    suspend fun getValidCachedSection(section: String, minCachedAt: Long): List<CachedWorkEntity>

    @Query("SELECT * FROM cached_works WHERE section = :section ORDER BY position ASC")
    suspend fun getAnyCachedSection(section: String): List<CachedWorkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(works: List<CachedWorkEntity>)

    @Query("DELETE FROM cached_works WHERE section = :section")
    suspend fun clearSection(section: String)

    @Query("DELETE FROM cached_works")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceSection(section: String, works: List<CachedWorkEntity>) {
        clearSection(section)
        insertAll(works)
    }
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_works WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllWorks(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library_works WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library_works WHERE status = :status AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getByStatus(status: String): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library_works WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: String): LibraryEntity?

    @Query("SELECT * FROM library_works WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): LibraryEntity?

    @Query("SELECT * FROM library_works WHERE id = :id AND isDeleted = 0 LIMIT 1")
    fun observeById(id: String): Flow<LibraryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: LibraryEntity)

    @Query("UPDATE library_works SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM library_works WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("SELECT COUNT(*) FROM library_works WHERE isDeleted = 0")
    fun countTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM library_works WHERE status = 'COMPLETED' AND isDeleted = 0")
    fun countCompleted(): Flow<Int>

    @Query("SELECT COUNT(*) FROM library_works WHERE isFavorite = 1 AND isDeleted = 0")
    fun countFavorites(): Flow<Int>

    @Query("SELECT COUNT(*) FROM library_works WHERE status = :status AND isDeleted = 0")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT * FROM library_works WHERE isDeleted = 0")
    suspend fun getAllSnapshot(): List<LibraryEntity>

    @Query("SELECT * FROM library_works")
    suspend fun getAllSnapshotIncludingDeleted(): List<LibraryEntity>

    @Query("DELETE FROM library_works")
    suspend fun clearAll()
}
