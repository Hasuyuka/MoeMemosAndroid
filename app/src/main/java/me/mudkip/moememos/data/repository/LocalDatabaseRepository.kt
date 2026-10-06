package me.mudkip.moememos.data.repository

import android.net.Uri
import androidx.core.net.toUri
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.mudkip.moememos.data.local.FileStorage
import me.mudkip.moememos.data.local.dao.MemoDao
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.local.entity.MemoWithResources
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.User
import me.mudkip.moememos.util.extractCustomTags
import me.mudkip.moememos.util.FileTrace
import okhttp3.MediaType
import java.time.Instant
import java.util.UUID

class LocalDatabaseRepository(
    private val memoDao: MemoDao,
    private val fileStorage: FileStorage,
    private val account: Account.Local = Account.Local(),
) : AbstractMemoRepository() {
    private val accountKey = account.accountKey()

    override fun observeMemos(): Flow<List<MemoEntity>> {
        return memoDao.observeAllMemos(accountKey).map { memos ->
            memos.map { it.toMemoEntity() }
        }
    }

    override suspend fun listMemos(): ApiResponse<List<MemoEntity>> {
        return try {
            // 与 SyncingRepository 一致：走 JOIN 查询，避免逐条查资源表的 N+1。
            val memos = memoDao.getAllMemosWithResources(accountKey).map { it.toMemoEntity() }
            ApiResponse.Success(memos)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun getMemo(identifier: String): MemoEntity? {
        return memoDao.getMemoById(identifier, accountKey)?.let { withResources(it) }
    }

    override suspend fun listMemosFiltered(
        tag: String?,
        pinnedOnly: Boolean,
        limit: Int
    ): ApiResponse<List<MemoEntity>> {
        return try {
            ApiResponse.Success(memoDao.getMemosFiltered(accountKey, tag, pinnedOnly, limit))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun randomMemo(): ApiResponse<MemoEntity?> {
        return try {
            ApiResponse.Success(memoDao.getRandomMemo(accountKey))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun listArchivedMemos(): ApiResponse<List<MemoEntity>> {
        return try {
            val memos = memoDao.getArchivedMemosWithResources(accountKey).map { it.toMemoEntity() }
            ApiResponse.Success(memos)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resources: List<ResourceEntity>,
        tags: List<String>?,
        deferPush: Boolean
    ): ApiResponse<MemoEntity> {
        return try {
            val now = Instant.now()
            val memo = MemoEntity(
                identifier = UUID.randomUUID().toString(),
                accountKey = accountKey,
                content = content,
                date = now,
                visibility = visibility,
                pinned = false,
                archived = false,
                needsSync = false,
                isDeleted = false,
                lastModified = now,
                lastSyncedAt = now
            )
            memoDao.insertMemo(memo)

            resources.forEach { resource ->
                memoDao.insertResource(
                    resource.copy(
                        accountKey = accountKey,
                        memoId = memo.identifier
                    )
                )
            }

            ApiResponse.Success(withResources(memo))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun updateMemo(
        identifier: String,
        content: String?,
        resources: List<ResourceEntity>?,
        visibility: MemoVisibility?,
        tags: List<String>?,
        pinned: Boolean?,
        deferPush: Boolean
    ): ApiResponse<MemoEntity> {
        return try {
            val existingMemo = memoDao.getMemoById(identifier, accountKey)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))

            val updatedAt = Instant.now()
            val updatedMemo = existingMemo.copy(
                content = content ?: existingMemo.content,
                visibility = visibility ?: existingMemo.visibility,
                pinned = pinned ?: existingMemo.pinned,
                lastModified = updatedAt,
                lastSyncedAt = updatedAt,
                needsSync = false,
                isDeleted = false
            )
            memoDao.insertMemo(updatedMemo)

            if (resources != null) {
                // 只写入/更新，**绝不删除**。
                //
                // 这里以前会把「不在传入列表里」的附件连数据库行带本地文件一起删掉，
                // 而传入列表来自调用方的内存状态（编辑器的 uploadResources、详情页
                // 勾复选框时传的 memo.resources），它可能是陈旧或不完整的。
                // 后果是：用户只是往已有备忘里加了一张图，保存时另外几张被当成
                // 「已被移除」销毁了。beta.20 的文件流水抓到了现场：
                //
                //   10:46:15 createMemo 传附件=4          ← 新建时确实有 4 张
                //   10:46:27 editMemo   传附件=2          ← 编辑器只拿到 2 张
                //   10:46:27 updateMemo 删多余附件 6ffac48
                //   10:46:27 删 b04262568_…_1000018053.jpg 存在=true
                //
                // 附件的移除本来就是**显式**的：编辑器里点图片菜单的「移除」、
                // 排序对话框里的删除，走的都是 deleteResource。所以这段删除是冗余的，
                // 去掉不会漏清理。
                resources.forEach { resource ->
                    memoDao.insertResource(
                        resource.copy(
                            accountKey = accountKey,
                            memoId = identifier
                        )
                    )
                }
            }

            ApiResponse.Success(withResources(updatedMemo))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun deleteMemo(identifier: String): ApiResponse<Unit> {
        return try {
            val memo = memoDao.getMemoById(identifier, accountKey)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))
            memoDao.getMemoResources(identifier, accountKey).forEach { resource ->
                deleteLocalFile(resource)
                memoDao.deleteResource(resource)
            }
            memoDao.deleteMemo(memo)
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun archiveMemo(identifier: String): ApiResponse<Unit> {
        return try {
            val memo = memoDao.getMemoById(identifier, accountKey)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))
            val now = Instant.now()
            memoDao.insertMemo(
                memo.copy(
                    archived = true,
                    needsSync = false,
                    lastModified = now,
                    lastSyncedAt = now
                )
            )
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun restoreMemo(identifier: String): ApiResponse<Unit> {
        return try {
            val memo = memoDao.getMemoById(identifier, accountKey)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))
            val now = Instant.now()
            memoDao.insertMemo(
                memo.copy(
                    archived = false,
                    needsSync = false,
                    lastModified = now,
                    lastSyncedAt = now
                )
            )
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun listTags(): ApiResponse<List<String>> {
        return try {
            val tags = memoDao.getAllMemos(accountKey)
                .asSequence()
                .flatMap { extractCustomTags(it.content).asSequence() }
                .filter { it.isNotBlank() }
                .toSet()
                .sorted()
            ApiResponse.Success(tags)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun listResources(): ApiResponse<List<ResourceEntity>> {
        return try {
            val resources = memoDao.getAllResources(accountKey)
            ApiResponse.Success(resources)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentUri: Uri,
        memoIdentifier: String?
    ): ApiResponse<ResourceEntity> {
        return try {
            val uri = fileStorage.saveFile(
                accountKey = accountKey,
                sourceUri = contentUri,
                filename = UUID.randomUUID().toString() + "_" + filename
            )
            val resource = ResourceEntity(
                identifier = UUID.randomUUID().toString(),
                remoteId = null,
                accountKey = accountKey,
                date = Instant.now(),
                filename = filename,
                uri = uri.toString(),
                localUri = uri.toString(),
                mimeType = type?.toString(),
                memoId = memoIdentifier
            )
            memoDao.insertResource(resource)
            FileTrace.record("建附件 ${resource.identifier.take(8)} ${filename.takeLast(16)} memoId=${memoIdentifier?.take(8) ?: "null"}")
            ApiResponse.Success(resource)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun deleteResource(identifier: String): ApiResponse<Unit> {
        return try {
            val resource = memoDao.getResourceById(identifier, accountKey)
                ?: return ApiResponse.Failure.Exception(Exception("Resource not found"))
            deleteLocalFile(resource)
            memoDao.deleteResource(resource)
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun getCurrentUser(): ApiResponse<User> {
        return ApiResponse.Success(account.toUser())
    }

    private suspend fun withResources(memo: MemoEntity): MemoEntity {
        val resources = memoDao.getMemoResources(memo.identifier, accountKey)
        return memo.copy().also { it.resources = resources }
    }

    private fun deleteLocalFile(resource: ResourceEntity) {
        val local = resource.localUri ?: resource.uri
        val localUri = local.toUri()
        if (localUri.scheme == "file") {
            FileTrace.record("删附件 ${resource.identifier.take(8)} ${resource.filename.takeLast(16)}")
            fileStorage.deleteFile(localUri)
        }
    }

}

private fun MemoWithResources.toMemoEntity(): MemoEntity {
    return memo.copy().also { it.resources = resources }
}
