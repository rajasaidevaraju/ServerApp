package database.dao

import androidx.lifecycle.LiveData
import database.entity.FileMeta
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT fileId, file_name FROM file_meta ORDER BY fileId DESC LIMIT :pageSize OFFSET :offset")
    fun getFilesPaginated(offset:Int, pageSize:Int): List<SimplifiedFileMeta>

    @Query("SELECT COUNT(*) FROM file_meta")
    fun getTotalFileCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM file_meta WHERE file_name = :fileName)")
    fun isFileNamePresent(fileName: String): Boolean

    @Query("SELECT COUNT(*) FROM file_meta")
    fun getTotalFileCountLive(): LiveData<Int>

    @Query("SELECT file_name FROM file_meta")
    fun getAllFileNames(): List<String>

    @Query("SELECT * FROM file_meta WHERE fileId = :fileId")
    fun getFileById(fileId: Long): FileMeta?

    @Query("SELECT screenshot_data FROM file_meta WHERE fileId = :fileId")
    fun getScreenshotData(fileId: Long): String?

    @Update
    fun updateFile(fileMeta: FileMeta)

    @Update
    fun updateFiles(fileMetas: List<FileMeta>): Int

    @Query("UPDATE file_meta SET screenshot_data = :screenshotData WHERE fileId = :fileId")
    fun updateScreenshotData(fileId: Long, screenshotData: String)

    @Delete
    fun deleteFile(fileMeta: FileMeta)

    @Query("SELECT * FROM file_meta INNER JOIN video_category_cross_ref ON file_meta.fileId = video_category_cross_ref.fileId INNER JOIN actress ON video_category_cross_ref.categoryId = actress.actressId")
    fun getFilesAndActress(): Map<FileMeta, List<Actress>>
    @Query("""
        SELECT  file_meta.fileId as fileId,  file_meta.file_name as fileName, actress.actressId as performerId, actress.name as performerName
        FROM file_meta
        LEFT JOIN video_actress_cross_ref ON file_meta.fileId = video_actress_cross_ref.fileId
        LEFT JOIN actress ON video_actress_cross_ref.actressId = actress.actressId
        WHERE file_meta.fileId = :fileId
    """)
    fun getFileWithPerformers(fileId: Long): List<FileDetailsWithPerformers>


    @Query("""SELECT file_meta.fileId, file_meta.file_name FROM file_meta
            INNER JOIN video_actress_cross_ref ON file_meta.fileId = video_actress_cross_ref.fileId
            WHERE video_actress_cross_ref.actressId=:performerId
            ORDER BY file_meta.fileId DESC LIMIT :pageSize OFFSET :offset""")
    fun getFilesWithPerformerPaginated(offset:Int, pageSize:Int,performerId:Long):List<SimplifiedFileMeta>
}

data class Item(
    val id: Long,
    val name: String
)

data class FileDetails(
    val name: String,
    val id: Long,
    val performers: List<Item>
)


data class FileDetailsWithPerformers(
    val fileId: Long,
    val fileName: String,
    val performerId: Long?,
    val performerName: String?
)
