package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.dao.BikeDao
import com.example.data.database.dao.RideDao
import com.example.data.database.dao.TrackPointDao
import com.example.data.database.dao.UserAccountDao
import com.example.data.database.dao.UserProfileDao
import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import com.example.data.database.entity.UserAccountEntity
import com.example.data.database.entity.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        RideEntity::class,
        TrackPointEntity::class,
        BikeEntity::class,
        UserProfileEntity::class,
        UserAccountEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class VeloSenseDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun bikeDao(): BikeDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userAccountDao(): UserAccountDao

    companion object {
        @Volatile
        private var INSTANCE: VeloSenseDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bikes ADD COLUMN wheelSize TEXT NOT NULL DEFAULT '29\"'")
                db.execSQL("ALTER TABLE bikes ADD COLUMN chainring TEXT NOT NULL DEFAULT '32T'")
                db.execSQL("ALTER TABLE bikes ADD COLUMN cassette TEXT NOT NULL DEFAULT '11-50T'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaUploadStatus TEXT NOT NULL DEFAULT 'NOT_SYNCED'")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaActivityId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaUploadedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaError TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_accounts (
                        id TEXT PRIMARY KEY NOT NULL,
                        googleAccountId TEXT,
                        email TEXT,
                        displayName TEXT NOT NULL,
                        photoUrl TEXT,
                        createdAt INTEGER NOT NULL,
                        lastLogin INTEGER NOT NULL,
                        isGuest INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): VeloSenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VeloSenseDatabase::class.java,
                    "velosense_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            private suspend fun populateInitialData(database: VeloSenseDatabase) {
                // Initialize default profile
                database.userProfileDao().insertProfile(
                    UserProfileEntity(
                        id = 1,
                        name = "Ciclista Velo",
                        weightKg = 72.0,
                        heightCm = 175.0,
                        totalXp = 0,
                        level = 1,
                        units = "METRIC",
                        autoPause = true,
                        keepScreenOn = true
                    )
                )

                // Initialize default bikes as requested: Trek MTB, Bicicleta carretera
                database.bikeDao().insertBike(
                    BikeEntity(
                        name = "Trek Fuel EX MTB",
                        type = "MTB",
                        weightKg = 13.5,
                        isDefault = true,
                        wheelSize = "29\"",
                        chainring = "32T",
                        cassette = "11-50T"
                    )
                )
                database.bikeDao().insertBike(
                    BikeEntity(
                        name = "Bicicleta Carretera Aero",
                        type = "Carretera",
                        weightKg = 8.6,
                        isDefault = false,
                        wheelSize = "700c",
                        chainring = "50/34T",
                        cassette = "11-32T"
                    )
                )
                database.bikeDao().insertBike(
                    BikeEntity(
                        name = "Gravel Explorer Carbon",
                        type = "Gravel",
                        weightKg = 9.8,
                        isDefault = false,
                        wheelSize = "700c",
                        chainring = "40T",
                        cassette = "11-42T"
                    )
                )
            }
        }
    }
}
