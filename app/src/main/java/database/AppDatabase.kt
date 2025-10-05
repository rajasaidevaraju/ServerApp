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
import database.dao.UserDao
import database.dao.VideoActressCrossRefDAO
import database.dao.VideoCategoryCrossRefDAO
import database.entity.Actress
import database.entity.Category
import database.entity.User
import database.jointable.VideoActressCrossRef
import database.jointable.VideoCategoryCrossRef


@Database(
    entities = [FileMeta::class, Actress::class, Category::class, VideoActressCrossRef::class, VideoCategoryCrossRef::class, User::class],
    version = 6,exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDAO
    abstract fun actressDao(): ActressDAO
    abstract fun categoryDao(): CategoryDAO
    abstract fun videoActressCrossRefDao(): VideoActressCrossRefDAO
    abstract fun videoCategoryCrossRefDao(): VideoCategoryCrossRefDAO
    abstract fun userDao(): UserDao
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).build()
                INSTANCE = instance
                return instance
            }
        }
        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE file_meta ADD COLUMN screenshot_data TEXT")
            }
        }
        private val MIGRATION_2_3:Migration=object :Migration(2,3){
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        user_name TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        salt TEXT,
                        created_at INTEGER NOT NULL,
                        last_login INTEGER,
                        disabled INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_user_name ON users(user_name)")

            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Create a new table with foreign keys
                db.execSQL("""
            CREATE TABLE video_actress_cross_ref_new (
                fileId INTEGER NOT NULL,
                actressId INTEGER NOT NULL,
                PRIMARY KEY (fileId, actressId),
                FOREIGN KEY (fileId) REFERENCES file_meta(fileId) ON DELETE CASCADE,
                FOREIGN KEY (actressId) REFERENCES actress(actressId) ON DELETE CASCADE
            )
        """.trimIndent())

                // Step 2: Delete old table
                db.execSQL("DROP TABLE video_actress_cross_ref")

                // Step 3: Rename new table to old table name
                db.execSQL("ALTER TABLE video_actress_cross_ref_new RENAME TO video_actress_cross_ref")
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Create a new table with foreign keys
                db.execSQL("""
            CREATE TABLE video_category_cross_ref_new (
                fileId INTEGER NOT NULL,
                categoryId INTEGER NOT NULL,
                PRIMARY KEY (fileId, categoryId),
                FOREIGN KEY (fileId) REFERENCES file_meta(fileId) ON DELETE CASCADE,
                FOREIGN KEY (categoryId) REFERENCES category(categoryId) ON DELETE CASCADE
            )
        """.trimIndent())


                // Step 2: Delete old table
                db.execSQL("DROP TABLE video_category_cross_ref")

                // Step 3: Rename new table to old table name
                db.execSQL("ALTER TABLE video_category_cross_ref_new RENAME TO video_category_cross_ref")
            }
        }
        private val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE file_meta ADD COLUMN file_size_bytes INTEGER NOT NULL DEFAULT 0")
            }
        }





    }
}