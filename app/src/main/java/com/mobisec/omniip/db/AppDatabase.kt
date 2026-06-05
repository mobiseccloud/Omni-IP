package com.mobisec.omniip.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `firewall_rules` ADD COLUMN `blockWifi` INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE `firewall_rules` ADD COLUMN `blockMobile` INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `connection_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `destIp` TEXT NOT NULL, `destPort` INTEGER NOT NULL, `asn` TEXT, `countryCode` TEXT, `city` TEXT, `appName` TEXT NOT NULL, `action` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `threat_feed_rules` (`targetValue` TEXT NOT NULL, `feedType` TEXT NOT NULL, PRIMARY KEY(`targetValue`))")
    }
}

@Database(entities = [FirewallRule::class, ThreatFeedRule::class, ConnectionLog::class, GeoRule::class], version = 6, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun firewallRuleDao(): FirewallRuleDao
    abstract fun threatFeedRuleDao(): ThreatFeedRuleDao
    abstract fun geoRuleDao(): GeoRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omni_ip_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
