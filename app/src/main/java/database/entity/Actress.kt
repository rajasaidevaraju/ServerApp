package database.entity;

import androidx.annotation.NonNull
import androidx.room.Entity;
import androidx.room.PrimaryKey

@Entity(tableName = "actress")
data class Actress(
        @PrimaryKey(autoGenerate = true)
        val actressId: Long = 0,
        var name: String
)

data class ActressIdName(
        val id: Long,
        val name: String
)

data class ActressCount(
        val id: Long,
        val name: String,
        val count: Int
)

fun ActressIdName.toActress(): Actress {
        return Actress(
                actressId = this.id,
                name = this.name
        )
}
