package database

import android.content.Context
import database.dao.FileDAO
import database.entity.FileMeta
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import database.dao.ActressDAO
import database.dao.CategoryDAO
import database.dao.VideoActressCrossRefDAO
import database.dao.VideoCategoryCrossRefDAO
import database.entity.Actress
import database.entity.Category
import database.jointable.VideoActressCrossRef
import database.jointable.VideoCategoryCrossRef


@Database(
    entities = [FileMeta::class, Actress::class, Category::class, VideoActressCrossRef::class, VideoCategoryCrossRef::class],
    version = 2,exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDAO
    abstract fun actressDao(): ActressDAO
    abstract fun categoryDao(): CategoryDAO
    abstract fun videoActressCrossRefDao(): VideoActressCrossRefDAO
    abstract fun videoCategoryCrossRefDao(): VideoCategoryCrossRefDAO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                return instance
            }
        }
        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Perform the necessary migrations here
                database.execSQL("ALTER TABLE file_meta ADD COLUMN screenshot_data TEXT")
            }
        }
    }
}