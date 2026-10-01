package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val usersList: List<UserData> = emptyList(),
    val currentUser: String = "",
    val appLockEnabled: Boolean = false,
    /**
     * 写入 settings_v3.json 时的结构版本。
     *
     * - 写入时总会带上（序列化器开了 encodeDefaults）。
     * - 读取时若 JSON 里**没有**这个键，说明是引入版本字段之前写下的文件，
     *   按 [LEGACY_SCHEMA_VERSION] 处理，由 SettingsMigrations 逐级升级。
     *
     * 注意：这里的版本号与文件名里的 "v3" 无关，后者是历史遗留的后缀。
     */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        /** 该字段在 JSON 里的键名。 */
        const val SCHEMA_VERSION_KEY = "schemaVersion"

        /** 引入版本字段之前写下的文件，结构上等同于 v1。 */
        const val LEGACY_SCHEMA_VERSION = 1

        /**
         * 当前结构版本。
         *
         * 改动本文件、[UserData] 或 [UserSettings] 的结构时：
         *  1. 把这里 +1；
         *  2. 在 SettingsMigrations 里补一条对应迁移；
         *  3. 在 SettingsSerializerTest 里补一个用例。
         */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
