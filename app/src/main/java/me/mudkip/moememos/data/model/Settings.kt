package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val usersList: List<UserData> = emptyList(),
    val currentUser: String = "",
    val appLockEnabled: Boolean = false,
    /** 明暗模式：跟随系统 / 浅色 / 深色。全局外观偏好，不随账户变化。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 是否使用 Material You 动态取色。仅 Android 12（API 31）及以上生效。 */
    val dynamicColor: Boolean = true,
    /** 字号档位。默认不放大，与引入该设置之前的行为一致。 */
    val fontScale: FontScale = FontScale.DEFAULT,
    /** 灵感页布局。默认大卡片，与引入该设置之前的行为一致。 */
    val exploreLayout: ExploreLayout = ExploreLayout.LARGE,
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
         * **只在需要变换既有数据时才 +1**（字段改名、拆分或合并字段、改变语义）。
         *
         * 仅仅新增一个**带默认值**的字段是向后兼容的：旧文件解码时该字段自然落到
         * 默认值，不需要迁移，也不需要动这个版本号。为了「让机制看起来被用过」
         * 而伪造一条空迁移只会增加噪音——本次新增 [themeMode] 与 [dynamicColor]
         * 就属于这种情况，因此版本号保持 1。
         *
         * 确实需要 +1 时：
         *  1. 把这里 +1；
         *  2. 在 SettingsMigrations 里补一条对应迁移；
         *  3. 在 SettingsSerializerTest 里补一个用例。
         */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
