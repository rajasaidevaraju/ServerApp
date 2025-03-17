package database.jointable;

import androidx.room.Entity;
import androidx.room.ForeignKey
import database.entity.Actress

@Entity(tableName = "video_actress_cross_ref", primaryKeys = ["fileId", "actressId"],
        foreignKeys = [
        ForeignKey(entity = database.entity.FileMeta::class,
                parentColumns = ["fileId"],
                childColumns = ["fileId"],
                onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Actress::class,
                parentColumns = ["actressId"],
                childColumns = ["actressId"],
                onDelete = ForeignKey.CASCADE)
])
data class VideoActressCrossRef(
        val fileId: Long,
        val actressId: Long
)

