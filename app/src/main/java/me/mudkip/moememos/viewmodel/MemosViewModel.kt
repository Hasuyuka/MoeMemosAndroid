package me.mudkip.moememos.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mudkip.moememos.data.constant.MemosVersionSupport
import me.mudkip.moememos.data.constant.MoeMemosException
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.ExploreLayout
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.data.model.DailyUsageStat
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.SyncStatus
import me.mudkip.moememos.data.service.AccountService
import me.mudkip.moememos.data.service.MemoService
import me.mudkip.moememos.ext.getErrorMessage
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.widget.WidgetUpdater
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject

/**
 * 列表的滚动锚点。
 *
 * @param identifier 第一条可见备忘的 id（不是 index，index 会随增删漂移）
 * @param offset 该条内部的像素偏移
 * @param layout 记录时的布局；布局换了就不恢复——两者的位置单位没有可比性
 */
data class MemoScrollAnchor(
    val identifier: String,
    val offset: Int,
    val layout: ExploreLayout,
)

@HiltViewModel
class MemosViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    var memos = mutableStateListOf<MemoEntity>()
        private set
    var tags = mutableStateListOf<String>()
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var matrix by mutableStateOf(DailyUsageStat.initialMatrix)
        private set

    /**
     * 列表滚动锚点：离开列表时第一条可见备忘的 id、它的像素偏移，以及当时的布局。
     *
     * 记 id 而不是 index——列表增删会让 index 漂移，隔一天回来同一个 index
     * 早就不是同一篇了。
     *
     * 放在 ViewModel 而不是 `rememberSaveable`：打开备忘详情再返回时列表会整个
     * 重新组合，实测滚动位置会丢（用户反馈"点开一篇再返回就跳回最上面"），
     * ViewModel 在导航往返之间不会重建，才有可靠的落点。
     */
    var scrollAnchor: MemoScrollAnchor? by mutableStateOf(null)
        private set

    fun saveScrollAnchor(anchor: MemoScrollAnchor?) {
        scrollAnchor = anchor
    }

    /**
     * 归档备忘。**按需加载**：只有搜索页切到「包含归档」时才会去查，
     * 日常浏览不为它付出任何代价。底层是本地 DAO 查询（不走网络），所以代价很低。
     */
    var archivedMemos = mutableStateListOf<MemoEntity>()
        private set

    val host: StateFlow<String?> =
        accountService.currentAccount
            .map { it?.getAccountInfo()?.host }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val syncStatus: StateFlow<SyncStatus> =
        memoService.syncStatus.stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatus())

    private val initialLoad = MutableStateFlow(false)

    init {
        snapshotFlow { memos.toList() }
            .onEach { matrix = calculateMatrix() }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            try {
                loadMemosSnapshot()
            } finally {
                initialLoad.value = true
            }

            memoService.syncStatus
                .map { it.syncing }
                .distinctUntilChanged()
                .collectLatest { syncing ->
                    if (syncing) {
                        return@collectLatest
                    }
                    memoService.memos.collectLatest { latestMemos ->
                        applyMemos(latestMemos)
                    }
                }
        }
    }

    private suspend fun loadMemosSnapshot() {
        when (val response = memoService.getRepository().listMemos()) {
            is ApiResponse.Success -> {
                applyMemos(response.data)
            }
            else -> {
                errorMessage = response.getErrorMessage()
            }
        }
    }

    suspend fun refreshLocalSnapshot() = withContext(viewModelScope.coroutineContext) {
        loadMemosSnapshot()
    }

    /**
     * 加载归档备忘，供搜索页的「包含归档」范围使用。
     *
     * AbstractMemoRepository.listArchivedMemos 的实现是纯本地 DAO 查询
     * （SyncingRepository.kt:93），因此这里不会触发网络请求，重复调用也安全。
     */
    suspend fun loadArchivedMemos() = withContext(viewModelScope.coroutineContext) {
        when (val response = memoService.getRepository().listArchivedMemos()) {
            is ApiResponse.Success -> {
                archivedMemos.clear()
                archivedMemos.addAll(response.data)
            }
            else -> {
                errorMessage = response.getErrorMessage()
            }
        }
    }

    suspend fun awaitInitialLoad() {
        initialLoad.first { it }
    }

    private fun applyMemos(latestMemos: List<MemoEntity>) {
        memos.clear()
        memos.addAll(latestMemos)
        errorMessage = null
    }

    suspend fun loadMemos(syncAfterLoad: Boolean = true) = withContext(viewModelScope.coroutineContext) {
        if (syncAfterLoad) {
            val compatibility = accountService.checkCurrentAccountSyncCompatibility(isAutomatic = true)
            if (compatibility !is AccountService.SyncCompatibility.Allowed) {
                return@withContext
            }

            val syncResult = memoService.sync(false)
            if (syncResult is ApiResponse.Success) {
                WidgetUpdater.updateWidgets(appContext)
            } else {
                if (!syncResult.isAccessTokenInvalidFailure()) {
                    errorMessage = syncResult.getErrorMessage()
                }
            }
        }
    }

    suspend fun refreshMemos(allowHigherV1Version: String? = null): ManualSyncResult = withContext(viewModelScope.coroutineContext) {
        when (val compatibility = accountService.checkCurrentAccountSyncCompatibility(
            isAutomatic = false,
            allowHigherV1Version = allowHigherV1Version
        )) {
            is AccountService.SyncCompatibility.Blocked -> {
                return@withContext ManualSyncResult.Blocked(
                    compatibility.message ?: MemosVersionSupport.supportedVersionsMessage(appContext)
                )
            }
            is AccountService.SyncCompatibility.RequiresConfirmation -> {
                return@withContext ManualSyncResult.RequiresConfirmation(
                    version = compatibility.version,
                    message = compatibility.message
                )
            }
            AccountService.SyncCompatibility.Allowed -> Unit
        }

        val syncResult = memoService.sync(true)
        if (syncResult is ApiResponse.Success) {
            if (allowHigherV1Version != null) {
                accountService.rememberAcceptedUnsupportedSyncVersion(allowHigherV1Version)
            }
            WidgetUpdater.updateWidgets(appContext)
        } else {
            val message = syncResult.getErrorMessage()
            errorMessage = message
            return@withContext ManualSyncResult.Failed(message)
        }
        ManualSyncResult.Completed
    }

    private fun ApiResponse<Unit>.isAccessTokenInvalidFailure(): Boolean {
        return this is ApiResponse.Failure.Exception && this.throwable == MoeMemosException.accessTokenInvalid
    }

    fun loadTags() = viewModelScope.launch {
        memoService.getRepository().listTags().suspendOnSuccess {
            tags.clear()
            tags.addAll(data)
        }
    }

    suspend fun updateMemoPinned(memoIdentifier: String, pinned: Boolean) = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().updateMemo(memoIdentifier, pinned = pinned).suspendOnSuccess {
            updateMemo(data)
            // Update widgets after pinning/unpinning a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun editMemo(memoIdentifier: String, content: String, resourceList: List<ResourceEntity>?, visibility: MemoVisibility): ApiResponse<MemoEntity> = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().updateMemo(memoIdentifier, content, resourceList, visibility).suspendOnSuccess {
            updateMemo(data)
            // Update widgets after editing a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun archiveMemo(memoIdentifier: String) = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().archiveMemo(memoIdentifier).suspendOnSuccess {
            memos.removeIf { it.identifier == memoIdentifier }
            // Update widgets after archiving a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun deleteMemo(memoIdentifier: String) = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().deleteMemo(memoIdentifier).suspendOnSuccess {
            memos.removeIf { it.identifier == memoIdentifier }
            // Update widgets after deleting a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun cacheResourceFile(resourceIdentifier: String, downloadedUri: Uri): ApiResponse<Unit> = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().cacheResourceFile(resourceIdentifier, downloadedUri)
    }

    suspend fun getResourceById(resourceIdentifier: String): ResourceEntity? = withContext(viewModelScope.coroutineContext) {
        when (val response = memoService.getRepository().listResources()) {
            is ApiResponse.Success -> response.data.firstOrNull { it.identifier == resourceIdentifier }
            else -> null
        }
    }

    private fun updateMemo(memo: MemoEntity) {
        val index = memos.indexOfFirst { it.identifier == memo.identifier }
        if (index != -1) {
            memos[index] = memo
        }
    }

    private fun calculateMatrix(): List<DailyUsageStat> {
        val countMap = HashMap<LocalDate, Int>()

        for (memo in memos) {
            val date = memo.date.atZone(OffsetDateTime.now().offset).toLocalDate()
            countMap[date] = (countMap[date] ?: 0) + 1
        }

        return DailyUsageStat.initialMatrix.map {
            it.copy(count = countMap[it.date] ?: 0)
        }
    }
}

val LocalMemos =
    compositionLocalOf<MemosViewModel> { error(me.mudkip.moememos.R.string.memos_view_model_not_found.string) }

sealed class ManualSyncResult {
    object Completed : ManualSyncResult()
    data class Blocked(val message: String) : ManualSyncResult()
    data class RequiresConfirmation(val version: String, val message: String) : ManualSyncResult()
    data class Failed(val message: String) : ManualSyncResult()
}
