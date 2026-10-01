package me.mudkip.moememos.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.mudkip.moememos.data.model.Settings

/**
 * settings_v3.json 的结构迁移。
 *
 * 约定：文件里的 `schemaVersion` 记录写入时的结构版本；读取时若低于
 * [Settings.CURRENT_SCHEMA_VERSION]，就按 [steps] 逐级升级。
 *
 * 新增一次结构变更的步骤：
 *  1. 把 [Settings.CURRENT_SCHEMA_VERSION] 加一；
 *  2. 在 [steps] 里补一条 `旧版本号 to { 变换 }`；
 *  3. 在 SettingsSerializerTest 里补一个用例。
 *
 * 找不到中间步骤时**直接抛异常**，而不是「尽力而为」地跳级——
 * 悄悄跳级会把旧结构当成新结构解析，进而破坏用户数据（账户列表就在这个文件里）。
 */
internal object SettingsMigrations {

    /** key = 该 JSON 的当前版本，value = 把它升级到 key + 1 的变换。 */
    private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun migrate(
        root: JsonObject,
        from: Int,
        to: Int = Settings.CURRENT_SCHEMA_VERSION,
    ): JsonObject = migrate(root, from, to, steps)

    /**
     * 抽出可注入 [steps] 的形式，便于用测试数据验证「逐级升级」与「缺失步骤」两个分支，
     * 而不必为了测试往生产代码里塞一条假迁移。
     */
    internal fun migrate(
        root: JsonObject,
        from: Int,
        to: Int,
        steps: Map<Int, (JsonObject) -> JsonObject>,
    ): JsonObject {
        var current = from
        var result = root
        while (current < to) {
            val step = steps[current]
                ?: throw IllegalStateException(
                    "缺少 settings 迁移步骤: v$current -> v${current + 1}"
                )
            val upgraded = step(result)
            // 由迁移管线统一盖版本号，避免每个步骤各自维护、漏盖或盖错。
            result = buildJsonObject {
                upgraded.forEach { (key, value) -> put(key, value) }
                put(Settings.SCHEMA_VERSION_KEY, JsonPrimitive(current + 1))
            }
            current += 1
        }
        return result
    }
}
