package me.mudkip.moememos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.local.entity.MemoWithResources
import me.mudkip.moememos.data.local.entity.ResourceEntity
import java.time.Instant

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE accountKey = :accountKey AND archived = 1 ORDER BY date DESC")
    suspend fun getArchivedMemos(accountKey: String): List<MemoEntity>

    @Query("""
        SELECT * FROM memos 
        WHERE accountKey = :accountKey AND archived = 0 AND isDeleted = 0
        ORDER BY pinned DESC, date DESC
    """)
    suspend fun getAllMemos(accountKey: String): List<MemoEntity>

    @Transaction
    @Query("""
        SELECT * FROM memos
        WHERE accountKey = :accountKey AND archived = 0 AND isDeleted = 0
        ORDER BY pinned DESC, date DESC
    """)
    fun observeAllMemos(accountKey: String): Flow<List<MemoWithResources>>

    /**
     * 与 [observeAllMemos] 同一条件的挂起版本，供一次性加载使用。
     *
     * 存在的意义：此前一次性加载走 [getAllMemos] 之后逐条调用 `getMemoResources`，
     * 是典型的 N+1——5000 条备忘要发 5001 次查询（这正是 issue #369「5000 条加载很慢」
     * 的直接来源之一）。Room 会把 `@Relation` 解析成**一次**批量查询
     * （`WHERE memoId IN (...)`），因此这里总共只需 2 次。
     */
    @Transaction
    @Query("""
        SELECT * FROM memos
        WHERE accountKey = :accountKey AND archived = 0 AND isDeleted = 0
        ORDER BY pinned DESC, date DESC
    """)
    suspend fun getAllMemosWithResources(accountKey: String): List<MemoWithResources>

    /** [getArchivedMemos] 的 JOIN 版本，理由同上。 */
    @Transaction
    @Query("SELECT * FROM memos WHERE accountKey = :accountKey AND archived = 1 ORDER BY date DESC")
    suspend fun getArchivedMemosWithResources(accountKey: String): List<MemoWithResources>

    /**
     * 「列表面板」用的一次性查询：把筛选、排序与截断全部下推给 SQLite。
     *
     * 两个关键细节：
     *
     * 1. **标签匹配用 `instr` 而不是 `LIKE`。** SQLite 的 `LIKE` 对 ASCII 默认**不区分大小写**，
     *    而列表页的 `contentHasTag` 用的是大小写敏感的 `contains`（该语义有单元测试钉着）。
     *    `instr(content, X) > 0` 才是与 `contains` 等价且大小写敏感的形式。
     * 2. `'#' || :tag` 对应 `contentHasTag` 里的 `"#$tag"`。它同时覆盖层级标签
     *    （`#work/sub` 含 `#work`），所以不需要额外的 `"#$tag/"` 条件。
     *
     * 另外这里**不**用 @Relation 取 resources：调用方（桌面小组件）只渲染正文与时间，
     * 省掉那次 JOIN 正是本查询存在的意义之一。
     */
    @Query("""
        SELECT * FROM memos
        WHERE accountKey = :accountKey AND archived = 0 AND isDeleted = 0
          AND (:pinnedOnly = 0 OR pinned = 1)
          AND (:tag IS NULL OR instr(content, '#' || :tag) > 0)
        ORDER BY pinned DESC, date DESC
        LIMIT :limit
    """)
    suspend fun getMemosFiltered(
        accountKey: String,
        tag: String?,
        pinnedOnly: Boolean,
        limit: Int
    ): List<MemoEntity>

    /**
     * 随机取一条备忘，供「回忆」面板使用。
     *
     * 调用方此前的做法是取回**整张表**再 `shuffled().firstOrNull()`——
     * 为了一条随机结果把上万条备忘全部读进内存。随机性交给 SQLite 即可。
     */
    @Query("""
        SELECT * FROM memos
        WHERE accountKey = :accountKey AND archived = 0 AND isDeleted = 0
        ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun getRandomMemo(accountKey: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE accountKey = :accountKey")
    suspend fun getAllMemosForSync(accountKey: String): List<MemoEntity>

    @Query("SELECT COUNT(*) FROM memos WHERE accountKey = :accountKey AND needsSync = 1")
    suspend fun countUnsyncedMemos(accountKey: String): Int

    @Query("SELECT * FROM memos WHERE identifier = :identifier AND accountKey = :accountKey")
    suspend fun getMemoById(identifier: String, accountKey: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE remoteId = :remoteId AND accountKey = :accountKey")
    suspend fun getMemoByRemoteId(remoteId: String, accountKey: String): MemoEntity?

    @Upsert
    suspend fun insertMemo(memo: MemoEntity)

    /**
     * Upserts [memo] only if its stored row still has [expectedLastModified], i.e. nothing wrote the
     * row since the caller read it. Returns false (and writes nothing) otherwise or if the row is gone.
     */
    @Transaction
    suspend fun insertMemoIfUnchanged(memo: MemoEntity, expectedLastModified: Instant): Boolean {
        val current = getMemoById(memo.identifier, memo.accountKey) ?: return false
        if (current.lastModified != expectedLastModified) {
            return false
        }
        insertMemo(memo)
        return true
    }

    @Delete
    suspend fun deleteMemo(memo: MemoEntity)

    // 必须按创建时间升序：附件顺序是用户选图的顺序，不能交给数据库随意返回。
    // 同一条备忘内的时间戳在写入时保证严格递增（见 nextResourceDate），
    // 所以这里不需要再加第二排序键。文件名留作兜底，避免历史数据并列时顺序不定。
    @Query(
        "SELECT * FROM resources WHERE memoId = :memoId AND accountKey = :accountKey " +
            "ORDER BY date ASC, filename ASC"
    )
    suspend fun getMemoResources(memoId: String, accountKey: String): List<ResourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: ResourceEntity)

    @Delete
    suspend fun deleteResource(resource: ResourceEntity)

    @Query("SELECT * FROM resources WHERE accountKey = :accountKey ORDER BY date DESC")
    suspend fun getAllResources(accountKey: String): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE identifier = :identifier AND accountKey = :accountKey")
    suspend fun getResourceById(identifier: String, accountKey: String): ResourceEntity?

    @Query("SELECT * FROM resources WHERE remoteId = :remoteId AND accountKey = :accountKey")
    suspend fun getResourceByRemoteId(remoteId: String, accountKey: String): ResourceEntity?

    @Query("DELETE FROM resources WHERE accountKey = :accountKey")
    suspend fun deleteResourcesByAccount(accountKey: String)

    @Query("DELETE FROM memos WHERE accountKey = :accountKey")
    suspend fun deleteMemosByAccount(accountKey: String)

}
