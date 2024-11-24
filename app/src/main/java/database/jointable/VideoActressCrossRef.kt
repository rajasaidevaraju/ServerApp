package database.jointable;

import androidx.room.Entity;

@Entity(tableName = "video_actress_cross_ref", primaryKeys = ["fileId", "actressId"])
data class VideoActressCrossRef(
        val fileId: Long,
        val actressId: Long
)

