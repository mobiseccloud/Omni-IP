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

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `integration_endpoints` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `baseUrl` TEXT NOT NULL, `apiKey` TEXT, `endpointType` TEXT NOT NULL, `actionPolicy` TEXT NOT NULL, `priorityLevel` INTEGER NOT NULL, `sequenceId` INTEGER NOT NULL)")
        database.execSQL("CREATE TABLE IF NOT EXISTS `heuristic_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `endpointId` INTEGER NOT NULL, `targetValue` TEXT NOT NULL, FOREIGN KEY(`endpointId`) REFERENCES `integration_endpoints`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_heuristic_rules_targetValue_endpointId` ON `heuristic_rules` (`targetValue`, `endpointId`)")
    }
}

@Database(entities = [FirewallRule::class, ThreatFeedRule::class, ConnectionLog::class, GeoRule::class, IntegrationEndpoint::class, HeuristicRule::class], version = 7, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun firewallRuleDao(): FirewallRuleDao
    abstract fun threatFeedRuleDao(): ThreatFeedRuleDao
    abstract fun geoRuleDao(): GeoRuleDao
    abstract fun integrationDao(): IntegrationDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
