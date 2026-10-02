package me.mudkip.moememos.util

import java.time.Instant

/**
 * 附件的创建时间：保证**同一条备忘内严格递增**。
 *
 * 一次选十几张图时，循环里的 `Instant.now()` 常常落在同一毫秒里。日期相同的话，
 * 排序就分不出先后，图片显示顺序会变成数据库返回的任意顺序——用户看到的就是
 * 「选了一堆图，顺序全乱了」。
 *
 * 做法很简单：只要不比上一张新，就比它晚 1 毫秒。既不用改已有数据，
 * 也让「按日期排序」这一条规则足以还原用户点选的先后。
 */
fun nextResourceDate(previous: Instant?, now: Instant): Instant =
    if (previous == null || now.isAfter(previous)) now else previous.plusMillis(1)

/**
 * 按给定顺序重新盖章日期：第 n 项至少比第 n-1 项晚 1 毫秒。
 *
 * 顺序在数据里只有「创建时间」这一个落点，所以用户排好序后必须把它写进去，
 * 否则下次读出来又是数据库返回的任意顺序。
 *
 * 写成与实体无关的泛型函数，是为了单元测试里直接用普通数据就能验证，
 * 不必拉起 Room。
 */
fun <T> restampDatesInOrder(
    items: List<T>,
    dateOf: (T) -> Instant,
    withDate: (T, Instant) -> T,
): List<T> {
    var previous: Instant? = null
    return items.map { item ->
        val stamped = nextResourceDate(previous, dateOf(item))
        previous = stamped
        withDate(item, stamped)
    }
}