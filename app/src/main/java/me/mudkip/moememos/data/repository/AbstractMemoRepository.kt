package me.mudkip.moememos.data.repository

import android.net.Uri
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.SyncStatus
import me.mudkip.moememos.data.model.User
import okhttp3.MediaType

abstract class AbstractMemoRepository {
    private val _syncStatus = MutableStateFlow(SyncStatus())
    open val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    abstract suspend fun listMemos(): ApiResponse<List<MemoEntity>>
    abstract suspend fun listArchivedMemos(): ApiResponse<List<MemoEntity>>

    /**
     * 按标签/置顶筛选并限制条数的备忘列表。
     *
     * 与 [listMemos] 的区别：筛选、排序与截断都在数据层完成，调用方拿到的是**最终结果**。
     * 桌面小组件这类只需要前几条的场景用它——否则每次刷新都要把整张表（含每条的资源）
     * 读进内存再自己过滤，备忘上万条时这个代价会直接体现在桌面刷新上。
     *
     * 注意：返回的实体**不挂载 `resources`**（正是为了省掉那次 JOIN）。需要资源的调用方用 [listMemos]。
     */
    abstract suspend fun listMemosFiltered(
        tag: String?,
        pinnedOnly: Boolean,
        limit: Int
    ): ApiResponse<List<MemoEntity>>

    /** 随机取一条备忘，供「回忆」场景使用；没有可用备忘时返回 `ApiResponse.Success(null)`。 */
    abstract suspend fun randomMemo(): ApiResponse<MemoEntity?>
    abstract suspend fun getMemo(identifier: String): MemoEntity?
    abstract suspend fun createMemo(content: String, visibility: MemoVisibility, resources: List<ResourceEntity>, tags: List<String>? = null, deferPush: Boolean = false): ApiResponse<MemoEntity>
    abstract suspend fun updateMemo(identifier: String, content: String? = null, resources: List<ResourceEntity>? = null, visibility: MemoVisibility? = null, tags: List<String>? = null, pinned: Boolean? = null, deferPush: Boolean = false): ApiResponse<MemoEntity>
    open suspend fun flushPendingPush(identifier: String) = Unit
    abstract suspend fun deleteMemo(identifier: String): ApiResponse<Unit>
    abstract suspend fun archiveMemo(identifier: String): ApiResponse<Unit>
    abstract suspend fun restoreMemo(identifier: String): ApiResponse<Unit>

    abstract suspend fun listTags(): ApiResponse<List<String>>

    abstract suspend fun listResources(): ApiResponse<List<ResourceEntity>>
    abstract suspend fun createResource(filename: String, type: MediaType?, contentUri: Uri, memoIdentifier: String? = null): ApiResponse<ResourceEntity>
    abstract suspend fun deleteResource(identifier: String): ApiResponse<Unit>

    abstract suspend fun getCurrentUser(): ApiResponse<User>

    open fun observeMemos(): Flow<List<MemoEntity>> = emptyFlow()

    open suspend fun cacheResourceFile(identifier: String, downloadedUri: Uri): ApiResponse<Unit> {
        return ApiResponse.Success(Unit)
    }

    open suspend fun sync(): ApiResponse<Unit> {
        return ApiResponse.Success(Unit)
    }

    open fun close() = Unit
}
