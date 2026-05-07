package com.talko.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.talko.app.data.local.dao.TalkoDao
import com.talko.app.data.local.entity.CallLogEntity
import com.talko.app.data.local.entity.ChatEntity
import com.talko.app.data.local.entity.MessageEntity

@Database(
    entities = [ChatEntity::class, MessageEntity::class, CallLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class TalkoDatabase : RoomDatabase() {
    abstract fun talkoDao(): TalkoDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS call_log (
                        id TEXT PRIMARY KEY NOT NULL,
                        peerId TEXT NOT NULL,
                        peerName TEXT NOT NULL,
                        callType TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        status TEXT NOT NULL,
                        durationSec INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
