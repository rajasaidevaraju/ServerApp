package database.jointable

import androidx.room.Entity
import androidx.room.ForeignKey
import database.entity.Category

@Entity(
    tableName = "video_category_cross_ref",
    primaryKeys = ["fileId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = database.entity.FileMeta::class,
            parentColumns = ["fileId"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Category::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE)
    ]
)
data class VideoCategoryCrossRef(
    val fileId: Long,
    val categoryId: Long
)