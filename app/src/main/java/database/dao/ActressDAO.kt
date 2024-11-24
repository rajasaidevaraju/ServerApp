package database.dao

import database.entity.Actress
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ActressDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertActress(actress: Actress)

    @Query("SELECT * FROM actress")
    fun getAllActresses(): List<Actress>

    @Query("SELECT * FROM actress WHERE actressId = :actressId")
    fun getActressById(actressId: Long): Actress?

    @Update
    fun updateActress(actress: Actress)

    @Delete
    fun deleteActress(actress: Actress)
}
