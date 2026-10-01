package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MemoEditGesture {
    NONE,
    SINGLE,
    DOUBLE,
    LONG,
}

@Serializable
data class UserSettings(
    val draft: String = "",
    val acceptedUnsupportedSyncVersions: List<String> = emptyList(),
    val editGesture: MemoEditGesture = MemoEditGesture.NONE,
    val autosave: Boolean = false,
    /**
     * 最近搜索词，最新一次在最前。
     *
     * 按账户保存：备忘本身按账户隔离，同一个查询词在不同的服务器上意义不同。
     * 只记录用户**明确提交**的搜索（按下输入法搜索键，或点开了某条结果），
     * 而不是每次敲键——否则历史里会塞满半截词。
     */
    val recentSearches: List<String> = emptyList(),
)
