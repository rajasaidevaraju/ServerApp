package database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index(value = ["user_name"], unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_name")
    val userName: String,
    @ColumnInfo(name = "password_hash")
    val passwordHash: String,
    val salt: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,
    @ColumnInfo(name = "last_login")
    val lastLogin: Long? = null,
    val disabled: Boolean = false
)
