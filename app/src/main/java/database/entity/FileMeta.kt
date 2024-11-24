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
    val fileUri: Uri,
    @ColumnInfo(name = "screenshot_data")
    val screenshotData: String? = null
)

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
