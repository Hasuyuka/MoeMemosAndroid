package me.mudkip.moememos.data.model

/**
 * [Settings] 的集中访问入口。
 *
 * 背景：设置分散在 [Settings]（全局）、[UserData]（账户）与 [UserSettings]（按账户偏好）
 * 三个数据类里。此前每次读写都要在调用点手写
 * `usersList.indexOfFirst { it.accountKey == currentUser }` + `toMutableList()` + `copy()`。
 * 这段逻辑在 `SettingsPage` 与 `MemoInputViewModel` 里重复了多次，任何一处写错
 * （漏判 -1、改错列表下标）都会静默破坏账户数据。
 *
 * 这里收敛成几个**纯函数**：
 * - 全部是 [Settings] 上的不可变变换，不碰存储、不碰协程，因此可以用普通 JVM 单元测试覆盖；
 * - 账户不存在时**原样返回**，而不是抛异常或静默新建——把拼错的 accountKey
 *   变成一条新账户，比什么都不做危险得多。
 *
 * 新增设置项时应当：在对应数据类加字段 → 如有需要在这里加读写辅助 → 在 UI 里调用。
 */

/** 按 accountKey 查账户；不存在返回 null。 */
fun Settings.userData(accountKey: String): UserData? =
    usersList.firstOrNull { it.accountKey == accountKey }

/** 当前选中的账户；未选择或已被移除时返回 null。 */
fun Settings.currentUserData(): UserData? = userData(currentUser)

/** 当前账户的按账户偏好；没有当前账户时返回默认值。 */
fun Settings.currentUserSettings(): UserSettings =
    currentUserData()?.settings ?: UserSettings()

/**
 * 不可变地更新指定账户。
 *
 * 账户不存在时原样返回，调用点不必自己判断。返回值可能是同一个实例
 * （`===` 相等），因此调用方可据此判断有无实际变化。
 */
fun Settings.updateUserData(
    accountKey: String,
    transform: (UserData) -> UserData,
): Settings {
    val index = usersList.indexOfFirst { it.accountKey == accountKey }
    if (index == -1) {
        return this
    }
    val updated = usersList.toMutableList()
    updated[index] = transform(updated[index])
    return copy(usersList = updated)
}

/** 不可变地更新当前账户的按账户偏好；没有当前账户时原样返回。 */
fun Settings.updateCurrentUserSettings(
    transform: (UserSettings) -> UserSettings,
): Settings = updateUserData(currentUser) { user ->
    user.copy(settings = transform(user.settings))
}
