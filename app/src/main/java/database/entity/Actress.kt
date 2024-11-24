package database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey

@Entity(tableName = "actress")
data class Actress(
        @PrimaryKey(autoGenerate = true)
        val actressId: Long = 0,
        val name: String
)
