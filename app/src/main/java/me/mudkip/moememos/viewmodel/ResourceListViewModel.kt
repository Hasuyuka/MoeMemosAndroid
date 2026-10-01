package me.mudkip.moememos.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.data.service.MemoService
import me.mudkip.moememos.ext.suspendOnErrorMessage
import javax.inject.Inject

@HiltViewModel
class ResourceListViewModel @Inject constructor(
    private val memoService: MemoService
): ViewModel() {
    var resources = mutableStateListOf<ResourceEntity>()
        private set

    /**
     * 首次加载前为 true，让页面一进入就显示加载态，而不是先闪一下空态。
     */
    var isLoading by mutableStateOf(true)
        private set

    /**
     * 此前资源页的加载失败是**完全静默**的：只调用 suspendOnSuccess，
     * 失败既不写日志也没有任何界面反馈，用户看到的就是一片空白。
     */
    var errorMessage: String? by mutableStateOf(null)
        private set

    fun loadResources() = viewModelScope.launch {
        isLoading = true
        memoService.getRepository().listResources()
            .suspendOnSuccess {
                resources.clear()
                resources.addAll(data.sortedByDescending { it.date })
                errorMessage = null
            }
            .suspendOnErrorMessage {
                errorMessage = it
            }
        isLoading = false
    }
}
