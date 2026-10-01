package me.mudkip.moememos.data.local

import androidx.room.migration.Migration

/**
 * 数据库结构迁移。
 *
 * 约定：改动 [MoeMemosDatabase] 涉及的实体、索引或 [Converters] 的结构时：
 *  1. 把 [MoeMemosDatabase.VERSION] 加一；
 *  2. 在 [ALL] 里补一条 `MIGRATION_n_(n+1)`；
 *  3. 跑 `MigrationsTest`（它会检查版本号与迁移列表是否配套）。
 *
 * **刻意不使用 `fallbackToDestructiveMigration`。**
 * 本应用是本地优先，数据库里就是用户的全部备忘。少一条迁移会让升级时抛
 * `IllegalStateException`——崩溃在这里是**想要**的行为：它比静默清库好得多，
 * 而且配套性检查会在合并前就把它拦下来。
 *
 * 目前 [ALL] 为空：数据库仍是 v1，尚未发生过结构变更。这个文件的存在是为了让
 * 第一次真正的结构变更（R2 的分页与全文检索需要加索引和 FTS 表）有地方可落，
 * 而不是等到那时候才现搭。
 */
internal object Migrations {

    /** 按起始版本升序排列的全部迁移。 */
    val ALL: List<Migration> = emptyList()
}
