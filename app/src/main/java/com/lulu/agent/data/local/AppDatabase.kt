package com.lulu.agent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lulu.agent.data.local.dao.CompanyDao
import com.lulu.agent.data.local.dao.JobDao
import com.lulu.agent.data.local.dao.LLMAuditDao
import com.lulu.agent.data.local.entity.CompanyEntity
import com.lulu.agent.data.local.entity.JobEntity
import com.lulu.agent.data.local.entity.LLMAuditLogEntity

@Database(
    entities = [
        JobEntity::class,
        CompanyEntity::class,
        LLMAuditLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun jobDao(): JobDao
    abstract fun companyDao(): CompanyDao
    abstract fun llmAuditDao(): LLMAuditDao

    companion object {
        private const val DB_NAME = "boss_agent.db"

        /**
         * v1 -> v2：jobs 表新增四维评分明细列 (多维评分体系)
         * 历史岗位的去重黑名单数据价值高，必须保留，禁用破坏性重建
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN tech_score INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE jobs ADD COLUMN experience_score INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE jobs ADD COLUMN salary_score INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE jobs ADD COLUMN stability_score INTEGER NOT NULL DEFAULT -1")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
