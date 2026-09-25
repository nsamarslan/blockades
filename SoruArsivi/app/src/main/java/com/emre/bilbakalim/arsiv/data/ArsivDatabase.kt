package com.emre.bilbakalim.arsiv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [QuestionEntity::class], version = 3, exportSchema = true)
abstract class ArsivDatabase : RoomDatabase() {

    abstract fun questionDao(): QuestionDao

    companion object {
        /** Sürüm 2: soru başına deneme/doğru sayaçları eklendi. Mevcut arşiv korunur. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN answeredCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN correctCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Sürüm 3: şıkların piksel imzaları (sembol şıkları ayırt etmek için).
         * Yalnızca boş bir sütun ekleniyor; mevcut kayıtlar olduğu gibi kalıyor,
         * imzalar sorular yeniden görüldükçe doluyor.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN optionSigs TEXT")
            }
        }

        @Volatile private var INSTANCE: ArsivDatabase? = null

        fun get(context: Context): ArsivDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArsivDatabase::class.java,
                    "soru_arsivi.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
