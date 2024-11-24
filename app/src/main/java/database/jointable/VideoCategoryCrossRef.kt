package database.jointable

import androidx.room.Entity

@Entity(
    tableName = "video_category_cross_ref",
    primaryKeys = ["fileId", "categoryId"]
)
data class VideoCategoryCrossRef(
    val fileId: Long,
    val categoryId: Long
)