package database.dao

import database.entity.FileMeta
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import database.entity.Actress
import database.entity.SimplifiedFileMeta

@Dao
interface FileDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFile(fileMeta: FileMeta)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFiles(fileMetas: List<FileMeta>):List<Long>

    @Query("SELECT * FROM file_meta")
    fun getAllFiles(): List<FileMeta>

    @Query("SELECT fileId, file_name FROM file_meta")
    fun getSimplifiedFilesMeta(): List<SimplifiedFileMeta>

    @Query("SELECT file_name FROM file_meta")
    fun getAllFileNames(): List<String>

    @Query("SELECT * FROM file_meta WHERE fileId = :fileId")
    fun getFileById(fileId: Long): FileMeta?

    @Query("SELECT screenshot_data FROM file_meta WHERE fileId = :fileId")
    fun getScreenshotData(fileId: Long): String?

    @Update
    fun updateFile(fileMeta: FileMeta)

    @Query("UPDATE file_meta SET screenshot_data = :screenshotData WHERE fileId = :fileId")
    fun updateScreenshotData(fileId: Long, screenshotData: String)

    @Delete
    fun deleteFile(fileMeta: FileMeta)

    @Query("SELECT * FROM file_meta INNER JOIN video_category_cross_ref ON file_meta.fileId = video_category_cross_ref.fileId INNER JOIN actress ON video_category_cross_ref.categoryId = actress.actressId")
    fun getFilesAndActress(): Map<FileMeta, List<Actress>>

}
