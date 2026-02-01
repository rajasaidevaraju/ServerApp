package database.dao

import android.net.Uri
import androidx.lifecycle.LiveData
import database.entity.FileMeta
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import database.entity.Actress
import database.entity.Converters

@Dao
@TypeConverters(Converters::class)
interface FileDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFile(fileMeta: FileMeta)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFiles(fileMetas: List<FileMeta>):List<Long>

    @Query("SELECT fileId, file_name,file_uri,file_size_bytes,duration_ms  FROM file_meta")
    fun getAllFiles(): List<FileMeta>

    @Query("SELECT fileId, file_name as fileName, file_size_bytes as fileSize, duration_ms AS durationMs  FROM file_meta ORDER BY fileId DESC LIMIT :pageSize OFFSET :offset")
    fun getFilesPaginated(offset:Int, pageSize:Int): List<FileMetaSimple>

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

    @Query("SELECT screenshot_binary FROM file_meta WHERE fileId = :fileId")
    fun getScreenshotDataBinary(fileId: Long): ByteArray?

    @Update
    fun updateFile(fileMeta: FileMeta)

    @Update
    fun updateFiles(fileMetas: List<FileMeta>): Int

    @Query("UPDATE file_meta SET file_uri = :uri WHERE fileId = :fileId")
    fun updateFileUri(fileId: Long, uri: String): Int

    @Query("UPDATE file_meta SET file_size_bytes = :fileSize WHERE fileId = :fileId")
    fun updateFileSize(fileId: Long, fileSize: Long): Int

    @Query("UPDATE file_meta SET duration_ms = :durationMs WHERE fileId = :fileId")
    fun updateFileDuration(fileId: Long, durationMs: Long): Int

    @Query("UPDATE file_meta SET screenshot_binary = :binary WHERE fileId = :fileId")
    fun updateScreenshotBinary(fileId: Long, binary: ByteArray)

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


    @Query("""SELECT file_meta.fileId as fileId, file_meta.file_name as fileName, file_meta.file_size_bytes as fileSize, file_meta.duration_ms as durationMs FROM file_meta
            INNER JOIN video_actress_cross_ref ON file_meta.fileId = video_actress_cross_ref.fileId
            WHERE video_actress_cross_ref.actressId=:performerId
            ORDER BY file_meta.fileId DESC LIMIT :pageSize OFFSET :offset""")
    fun getFilesWithPerformerPaginated(offset:Int, pageSize:Int,performerId:Long):List<FileMetaSimple>

    @RawQuery(observedEntities = [FileMeta::class])
    fun getFilesSorted(query: SupportSQLiteQuery): List<FileMetaSimple>

    @RawQuery(observedEntities = [FileMeta::class])
    fun getFilesSortedForPerformer(query: SupportSQLiteQuery): List<FileMetaSimple>

    @Query("SELECT fileId, file_uri AS fileUri FROM file_meta WHERE duration_ms = 0")
    fun getFilesMissingDurationSimple(): List<FileLocator>
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

data class FileLocator(
    val fileId: Long,
    val fileUri: Uri
)

data class FileMetaSimple(
    val fileId: Long,
    val fileName: String,
    val fileSize: Long,
    val durationMs: Long
)


data class FileDetailsWithPerformers(
    val fileId: Long,
    val fileName: String,
    val performerId: Long?,
    val performerName: String?
)
