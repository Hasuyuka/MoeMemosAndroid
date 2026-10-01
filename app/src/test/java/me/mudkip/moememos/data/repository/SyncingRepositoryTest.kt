package me.mudkip.moememos.data.repository

import android.content.Context
import androidx.room.Room
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.runBlocking
import me.mudkip.moememos.data.local.FileStorage
import me.mudkip.moememos.data.local.MoeMemosDatabase
import me.mudkip.moememos.data.local.dao.MemoDao
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.Resource
import me.mudkip.moememos.data.model.User
import okhttp3.MediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.InputStream
import java.time.Instant

/**
 * `SyncingRepository` 的离线队列行为。
 *
 * 需求文档在 REQ-602（批量操作）里写得很直白：
 *
 * > 批量 + 离线队列 + 冲突策略三者叠加，是最容易产生数据损坏的组合。
 * > **必须先为 `SyncingRepository` 的冲突算法补测试（当前零覆盖）再动手。**
 *
 * 这个文件补的就是这件事的第一半：**离线状态下批量操作之后，本地数据是否完好**。
 * 这一半不需要理解冲突算法，只需要「远端不可用时，本地库里的标记与条目一条不少」——
 * 而它恰好是数据损坏最可能的入口。
 *
 * 覆盖：
 * - 离线创建 / 删除 / 归档 / 恢复 各自的本地标记
 * - **批量 20 条离线删除**：全部成为墓碑、待同步计数正确、列表清空、无丢失
 * - 远端恢复后：批量离线创建的 20 条经 `sync()` 全部推送、待同步标记清零
 * - 远端始终不可用：`sync()` 失败，但本地 20 条一条不少
 *
 * 尚未覆盖（如实记录）：冲突算法本身（远端与本地同时修改同一条时的取舍）、
 * 以及「部分失败」场景下逐条重试的行为。它们需要更完整的远端行为模拟。
 *
 * 账户类型用 [Account.Local]：`SyncingRepository` 内部并不按账户类型分支
 * （分支在 `AccountService` 里——本地账户走的是 `LocalDatabaseRepository`），
 * 因此这里用什么账户不影响被测的队列语义。`deferredPushDelayMillis` 设得很长，
 * 是为了让「延迟推送」在测试期间不会突然插进来，保证断言是确定性的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncingRepositoryTest {

    private lateinit var db: MoeMemosDatabase
    private lateinit var dao: MemoDao
    private lateinit var fileStorage: FileStorage
    private lateinit var account: Account

    private val accountKey = "local"

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoeMemosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.memoDao()
        fileStorage = FileStorage(context)
        account = Account.Local()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repository(remote: RemoteRepository) = SyncingRepository(
        memoDao = dao,
        fileStorage = fileStorage,
        remoteRepository = remote,
        account = account,
        deferredPushDelayMillis = 3_600_000,
    )

    private suspend fun unsyncedCount() = dao.countUnsyncedMemos(accountKey)

    private suspend fun stored(id: String) = dao.getMemoById(id, accountKey)

    // ---------- 离线创建 ----------

    @Test
    fun `离线创建的备忘立刻在本地可见且待同步`() = runBlocking {
        val remote = FakeRemoteRepository(online = false)
        val repository = repository(remote)

        val result = repository.createMemo(
            content = "断网时写的一条",
            visibility = MemoVisibility.PRIVATE,
            resources = emptyList(),
            deferPush = true,
        )

        assertTrue("离线创建应当成功落库，而不是因为网络失败", result is ApiResponse.Success)
        assertEquals(1, dao.getAllMemos(accountKey).size)
        assertEquals(1, unsyncedCount())
        assertEquals("断网时写的一条", dao.getAllMemos(accountKey).first().content)
    }

    // ---------- 离线删除与归档：本地标记 ----------

    @Test
    fun `离线删除会留下待同步的墓碑`() = runBlocking {
        insertSyncedMemo("m1", "要被删掉的")
        val repository = repository(FakeRemoteRepository(online = false))

        val result = repository.deleteMemo("m1")

        assertTrue(result is ApiResponse.Success)
        val stored = stored("m1")
        assertTrue("删除应当留下墓碑，而不是直接把行删掉", stored != null && stored.isDeleted)
        assertTrue("墓碑必须仍待同步，否则服务端永远不会知道这条被删了", stored!!.needsSync)
        assertTrue("删除后的条目不应当再出现在列表里", dao.getAllMemos(accountKey).isEmpty())
        assertEquals(1, unsyncedCount())
    }

    @Test
    fun `离线归档与恢复只改标志且都待同步`() = runBlocking {
        insertSyncedMemo("m1", "要被归档的")
        val repository = repository(FakeRemoteRepository(online = false))

        assertTrue(repository.archiveMemo("m1") is ApiResponse.Success)
        assertTrue(stored("m1")!!.archived)
        assertTrue(stored("m1")!!.needsSync)
        assertTrue(dao.getArchivedMemos(accountKey).any { it.identifier == "m1" })

        assertTrue(repository.restoreMemo("m1") is ApiResponse.Success)
        assertFalse(stored("m1")!!.archived)
        assertTrue(stored("m1")!!.needsSync)
    }

    // ---------- 批量：文档点名的高风险场景 ----------

    @Test
    fun `断网时批量删除 20 条不会丢失任何一条`() = runBlocking {
        repeat(20) { index -> insertSyncedMemo("m$index", "第 $index 条") }
        val repository = repository(FakeRemoteRepository(online = false))

        repeat(20) { index ->
            assertTrue(repository.deleteMemo("m$index") is ApiResponse.Success)
        }

        // 列表里一条都不剩
        assertTrue(dao.getAllMemos(accountKey).isEmpty())
        // 但库里 20 条墓碑一条不少——这正是「本地优先」的承诺：
        // 删除不依赖网络，也不能因为网络不可用而丢掉删除动作（否则重连后条目会「复活」）。
        assertEquals(20, dao.getAllMemosForSync(accountKey).size)
        assertTrue(dao.getAllMemosForSync(accountKey).all { it.isDeleted && it.needsSync })
        assertEquals(20, unsyncedCount())
    }

    @Test
    fun `断网时批量归档 20 条全部进入归档列表`() = runBlocking {
        repeat(20) { index -> insertSyncedMemo("m$index", "第 $index 条") }
        val repository = repository(FakeRemoteRepository(online = false))

        repeat(20) { index ->
            assertTrue(repository.archiveMemo("m$index") is ApiResponse.Success)
        }

        assertTrue(dao.getAllMemos(accountKey).isEmpty())
        assertEquals(20, dao.getArchivedMemos(accountKey).size)
        assertEquals(20, unsyncedCount())
    }

    // ---------- 恢复网络后 ----------

    @Test
    fun `恢复网络后批量离线创建的备忘会被推送且标记清零`() = runBlocking {
        val remote = FakeRemoteRepository(online = false)
        val repository = repository(remote)

        repeat(20) { index ->
            repository.createMemo(
                content = "第 $index 条",
                visibility = MemoVisibility.PRIVATE,
                resources = emptyList(),
                deferPush = true,
            )
        }
        assertEquals(20, unsyncedCount())

        remote.online = true
        val syncResult = repository.sync()

        assertTrue("恢复网络后同步应当成功", syncResult is ApiResponse.Success)
        assertEquals("20 条都应当被推送到服务端", 20, remote.createdPayloads.size)
        assertEquals("推送成功后不应再有待同步条目", 0, unsyncedCount())
        assertTrue(
            "推送成功后本地条目应当带上服务端 id",
            dao.getAllMemosForSync(accountKey).all { !it.needsSync && it.remoteId != null },
        )
    }

    @Test
    fun `远端始终不可用时同步失败但本地一条不少`() = runBlocking {
        repeat(20) { index -> insertSyncedMemo("m$index", "第 $index 条") }
        val remote = FakeRemoteRepository(online = false)
        val repository = repository(remote)

        repository.sync()

        // 同步失败是可以接受的；本地数据受损不可以。
        assertEquals(20, dao.getAllMemosForSync(accountKey).size)
        assertTrue(remote.createdPayloads.isEmpty())
    }

    // ---------- 辅助 ----------

    /** 直接写一条「已经同步过」的备忘，省去先跑一次完整同步。 */
    private suspend fun insertSyncedMemo(identifier: String, content: String) {
        dao.insertMemo(
            MemoEntity(
                identifier = identifier,
                remoteId = "remote-$identifier",
                accountKey = accountKey,
                content = content,
                date = Instant.parse("2026-01-01T00:00:00Z"),
                visibility = MemoVisibility.PRIVATE,
                pinned = false,
                needsSync = false,
            )
        )
    }
}

/**
 * 只实现被测场景会走到的远端行为。
 *
 * [online] 为 false 时所有调用都返回失败——这就是「断网」的模拟。
 * 用 `Exception` 而不是抛异常，是因为 `SyncingRepository` 各处都按 `ApiResponse.Failure`
 * 分支处理，这样最贴近真实的失败路径。
 */
private class FakeRemoteRepository(var online: Boolean) : RemoteRepository() {

    val createdPayloads = mutableListOf<String>()

    private fun <T> offline(): ApiResponse<T> =
        ApiResponse.Failure.Exception(IllegalStateException("offline"))

    override suspend fun listMemos(): ApiResponse<List<Memo>> =
        if (online) ApiResponse.Success(emptyList()) else offline()

    override suspend fun listArchivedMemos(): ApiResponse<List<Memo>> =
        if (online) ApiResponse.Success(emptyList()) else offline()

    override suspend fun listWorkspaceMemos(
        pageSize: Int,
        pageToken: String?
    ): ApiResponse<Pair<List<Memo>, String?>> =
        if (online) ApiResponse.Success(emptyList<Memo>() to null) else offline()

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        tags: List<String>?,
        createdAt: Instant?
    ): ApiResponse<Memo> {
        if (!online) {
            return offline()
        }
        createdPayloads += content
        return ApiResponse.Success(
            Memo(
                remoteId = "remote-created-${createdPayloads.size}",
                content = content,
                date = createdAt ?: Instant.now(),
                pinned = false,
                visibility = visibility,
                resources = emptyList(),
                tags = tags ?: emptyList(),
            )
        )
    }

    override suspend fun updateMemo(
        remoteId: String,
        content: String?,
        resourceRemoteIds: List<String>?,
        visibility: MemoVisibility?,
        tags: List<String>?,
        pinned: Boolean?,
        archived: Boolean?
    ): ApiResponse<Memo> = offline()

    override suspend fun deleteMemo(remoteId: String): ApiResponse<Unit> =
        if (online) ApiResponse.Success(Unit) else offline()

    override suspend fun listResources(): ApiResponse<List<Resource>> =
        if (online) ApiResponse.Success(emptyList()) else offline()

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String?
    ): ApiResponse<Resource> = offline()

    override suspend fun deleteResource(remoteId: String): ApiResponse<Unit> = offline()

    override suspend fun getCurrentUser(): ApiResponse<User> =
        if (online) {
            ApiResponse.Success(User(identifier = "u1", name = "tester"))
        } else {
            offline()
        }
}
