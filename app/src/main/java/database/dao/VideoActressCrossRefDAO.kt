package database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.jointable.VideoActressCrossRef

@Dao
interface VideoActressCrossRefDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertVideoActressCrossRef(videoActressCrossRef: VideoActressCrossRef)

    @Query("SELECT * FROM video_actress_cross_ref WHERE fileId = :fileId")
    fun getActressesForVideo(fileId: Long): List<VideoActressCrossRef>

    @Query("SELECT * FROM video_actress_cross_ref WHERE actressId = :actressId")
    fun getVideosForActress(actressId: Long): List<VideoActressCrossRef>

    @Delete
    fun deleteVideoActressCrossRef(videoActressCrossRef: VideoActressCrossRef)
}
