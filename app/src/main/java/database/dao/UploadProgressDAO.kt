package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import database.entity.UploadProgress

@Dao
interface UploadProgressDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(uploadProgress: UploadProgress): Long

    @Update
    fun update(uploadProgress: UploadProgress)

    @Query("SELECT * FROM upload_progress WHERE file_name = :fileName AND target = :target LIMIT 1")
    fun getByFileNameAndTarget(fileName: String, target: String): UploadProgress?

    @Query("DELETE FROM upload_progress WHERE file_name = :fileName AND target = :target")
    fun deleteByFileNameAndTarget(fileName: String, target: String)

    @Query("UPDATE upload_progress SET uploaded_chunks = :uploadedChunks, updated_at = :updatedAt WHERE id = :id")
    fun updateProgress(id: Long, uploadedChunks: Int, updatedAt: Long = System.currentTimeMillis())

    @androidx.room.Delete
    fun delete(uploadProgress: UploadProgress)

    @Query("SELECT * FROM upload_progress")
    fun getAllUploadProgress(): List<UploadProgress>
}
