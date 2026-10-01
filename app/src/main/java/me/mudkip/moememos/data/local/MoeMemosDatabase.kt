package me.mudkip.moememos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.mudkip.moememos.data.local.dao.MemoDao
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.local.entity.ResourceEntity

@Database(
    entities = [MemoEntity::class, ResourceEntity::class],
    version = MoeMemosDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MoeMemosDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao

    companion object {
        /**
         * 数据库结构版本，唯一来源。
         *
         * 改动实体、索引或 Converters 的结构时 +1，并在 [Migrations.ALL] 里补一条
         * v(n) -> v(n+1) 的迁移。`MigrationsTest` 会检查两者是否配套——
         * 「加了版本号却忘了迁移」是能通过编译、只在用户升级时炸掉的那类疏忽。
         *
         * 用常量而不是字面量，是为了让版本号在测试里也能被安全引用（const 会编译期内联，
         * 不会触发加载这个继承自 RoomDatabase 的类）。
         */
        const val VERSION = 1

        @Volatile
        private var INSTANCE: MoeMemosDatabase? = null

        fun getDatabase(context: Context): MoeMemosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MoeMemosDatabase::class.java,
                    "moememos_database_localfirst"
                )
                    .addMigrations(*Migrations.ALL.toTypedArray())
                    // 刻意不调用 fallbackToDestructiveMigration：
                    // 本地优先模式下数据库就是用户的全部备忘，清库等于丢数据。
                    // 缺迁移时宁可崩溃——那至少是可诊断的，而且 MigrationsTest
                    // 会在合并前就拦下来。
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
