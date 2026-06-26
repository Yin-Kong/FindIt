package com.findit.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.findit.app.data.model.Item
import com.findit.app.data.model.ItemTagCrossRef
import com.findit.app.data.model.Location
import com.findit.app.data.model.Tag

@Database(
    entities = [
        Item::class,
        Tag::class,
        ItemTagCrossRef::class,
        Location::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new locations table
                db.execSQL("CREATE TABLE locations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, groupId INTEGER NOT NULL)")

                // 2. Copy place names as locations (deduplicate by name)
                db.execSQL("""
                    INSERT INTO locations (name, groupId) 
                    SELECT DISTINCT name, 0 FROM location_places
                """)

                // 3. Add new locationId column to items
                db.execSQL("ALTER TABLE items ADD COLUMN locationId INTEGER DEFAULT NULL")

                // 4. Map old placeId to new locationId (by name match)
                db.execSQL("""
                    UPDATE items SET locationId = (
                        SELECT l.id FROM locations l 
                        INNER JOIN location_places lp ON l.name = lp.name 
                        WHERE lp.id = items.placeId 
                        LIMIT 1
                    ) WHERE placeId IS NOT NULL
                """)

                // 5. Drop old tables
                db.execSQL("DROP TABLE IF EXISTS location_containers")
                db.execSQL("DROP TABLE IF EXISTS location_areas")
                db.execSQL("DROP TABLE IF EXISTS location_places")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "findit_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
