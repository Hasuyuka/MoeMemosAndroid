package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable
import me.mudkip.moememos.util.MemoSortMode

@Serializable
enum class MemoEditGesture {
    NONE,
    SINGLE,
    DOUBLE,

    /**
     * 已废弃：长按进入编辑这个选项从设置里去掉了，长按改成「进入批量编辑」。
     *
     * 但这个值**必须留着**：用户设置以 JSON 存在本地，里面可能已经写着 "LONG"，
     * 而 kotlinx.serialization 碰到未知枚举值会直接抛异常——删掉这个值等于让
     * 老用户一打开设置就崩。现在它只是不再被界面提供、不再有实际作用。
     */
    @Deprecated("长按已改为进入批量编辑，此选项不再提供")
    LONG,
}

/** 设置界面里可选的编辑手势——不含已废弃的 [MemoEditGesture.LONG]。 */
val selectableEditGestures: List<MemoEditGesture> =
    MemoEditGesture.entries.filter { it != MemoEditGesture.LONG }

@Serializable
data class UserSettings(
    val draft: String = "",
    val acceptedUnsupportedSyncVersions: List<String> = emptyList(),
    val editGesture: MemoEditGesture = MemoEditGesture.NONE,
    /** 列表排序方式。默认按创建时间，与引入该设置之前的行为一致。 */
    val memoSortMode: MemoSortMode = MemoSortMode.CREATED,
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
