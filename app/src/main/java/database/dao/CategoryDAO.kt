package database.dao

import database.entity.Category
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CategoryDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategory(category: Category)

    @Query("SELECT * FROM category")
    fun getAllCategories(): List<Category>

    @Query("SELECT * FROM category WHERE categoryId = :categoryId")
    fun getCategoryById(categoryId: Long): Category?

    @Update
    fun updateCategory(category: Category)

    @Delete
    fun deleteCategory(category: Category)
}
