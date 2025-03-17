package database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import database.jointable.VideoCategoryCrossRef

@Dao
interface VideoCategoryCrossRefDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertVideoCategoryCrossRef(videoCategoryCrossRef: VideoCategoryCrossRef):Long

    @Query("SELECT * FROM video_category_cross_ref WHERE fileId = :fileId")
    fun getCategoriesForVideo(fileId: Long): List<VideoCategoryCrossRef>

    @Query("SELECT * FROM video_category_cross_ref WHERE categoryId = :categoryId")
    fun getVideosForCategory(categoryId: Long): List<VideoCategoryCrossRef>

    @Delete
    fun deleteVideoCategoryCrossRef(videoCategoryCrossRef: VideoCategoryCrossRef)
}
