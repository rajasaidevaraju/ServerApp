package database.dao

import database.entity.Actress
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import database.entity.ActressIdName

@Dao
interface ActressDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertActress(actress: Actress)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertActresses(actresses: List<Actress>):List<Long>

    @Query("SELECT actressId AS id,name FROM actress")
    fun getAllActresses(): List<ActressIdName>

    @Query("SELECT * FROM actress WHERE actressId = :actressId")
    fun getActressById(actressId: Long): Actress?

    @Query("DELETE FROM actress WHERE actressId IN (:ids)")
    fun deleteActressesByIds(ids: List<Long>): Int

    @Update
    fun updateActress(actress: Actress):Int

    @Delete
    fun deleteActress(actress: Actress)
}
