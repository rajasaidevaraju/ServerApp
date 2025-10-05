package database.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "file_meta",
    indices = [Index(value = ["file_name"], unique = true), Index(value = ["file_uri"], unique = true)]
)
@TypeConverters(Converters::class)
data class FileMeta(
    @PrimaryKey(autoGenerate = true)
    val fileId: Long = 0,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_uri")
    var fileUri: Uri,
    @ColumnInfo(name = "screenshot_data")
    val screenshotData: String? = null,
    @ColumnInfo(name = "file_size_bytes", defaultValue = "0")
    val fileSize: Long=0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileMeta) return false
        return fileName == other.fileName && fileUri.toString() == other.fileUri.toString()
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + fileUri.hashCode()
        return result
    }
}

data class SimplifiedFileMeta(
    val fileId: Int,
    @ColumnInfo(name = "file_name")
    val fileName: String
)

class Converters {
    @TypeConverter
    fun fromUri(uri: Uri?): String? {
        return uri?.toString()
    }

    @TypeConverter
    fun toUri(uriString: String?): Uri? {
        return uriString?.let { Uri.parse(it) }
    }
}
