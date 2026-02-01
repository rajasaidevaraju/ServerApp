package database.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(
    tableName = "upload_progress",
    indices = [Index(value = ["file_name", "target"], unique = true)]
)
@TypeConverters(Converters::class)
data class UploadProgress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_uri")
    val fileUri: Uri,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "chunk_size")
    val chunkSize: Long,
    @ColumnInfo(name = "total_chunks")
    val totalChunks: Int,
    @ColumnInfo(name = "uploaded_chunks")
    var uploadedChunks: Int,
    @ColumnInfo(name = "target")
    val target: String, // INTERNAL | EXTERNAL
    @ColumnInfo(name = "status")
    var status: String, // NEW | IN_PROGRESS | COMPLETED
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis()
)
